package Dba;

import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SyncManager {

    private static final String TAG           = "BacheoPro_Sync";
    private static final String BASE_URL      = "http://192.168.1.89:3000";
    private static final String ENDPOINT_SYNC = BASE_URL + "/api/reportes";
    private static final int    MAX_INTENTOS  = 5;
    private static final int    TIMEOUT_MS    = 15_000;
    private static final String BOUNDARY      = "----BacheoBoundary" + System.currentTimeMillis();

    private final Context         context;
    private final DatabaseHelper  db;
    private final ExecutorService executor;
    private final Handler         mainHandler;

    public SyncManager(Context context) {
        this.context     = context.getApplicationContext();
        this.db          = new DatabaseHelper(this.context);
        this.executor    = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void sincronizarPendientes() {
        sincronizarPendientes(null);
    }

    public void sincronizarPendientes(SyncCallback callback) {
        if (!hayInternet()) {
            Log.d(TAG, "Sin internet. Sincronización pospuesta.");
            if (callback != null) callback.onSinInternet();
            return;
        }

        executor.execute(() -> {
            int subidos = 0;
            int errores = 0;

            Cursor cursor = db.obtenerPendientesSincronizacion();
            try {
                while (cursor.moveToNext()) {
                    long   idLocal     = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
                    String categoria   = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CATEGORIA));
                    String descripcion = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DESCRIPCION));
                    double lat         = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_LATITUD));
                    double lng         = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_LONGITUD));
                    String rutaFoto    = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RUTA_FOTO));
                    String ubicacion   = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UBICACION));
                    String fecha       = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_FECHA));

                    try {
                        Log.d(TAG, "Enviando reporte " + idLocal + " | lat=" + lat + " lng=" + lng);

                        // Subir con foto (multipart) si existe el archivo
                        int idServidor = -1;
                        File fotoFile = (rutaFoto != null && !rutaFoto.isEmpty()) ? new File(rutaFoto) : null;

                        if (fotoFile != null && fotoFile.exists()) {
                            idServidor = enviarConFoto(categoria, descripcion, lat, lng, ubicacion, fecha, fotoFile);
                        } else {
                            // Sin foto: JSON plano
                            JSONObject payload = new JSONObject();
                            payload.put("categoria",   categoria   != null ? categoria   : "");
                            payload.put("descripcion", descripcion != null ? descripcion : "");
                            payload.put("latitud",     lat);
                            payload.put("longitud",    lng);
                            payload.put("direccion",   ubicacion   != null ? ubicacion   : "");
                            payload.put("fecha",       fecha       != null ? fecha       : "");
                            idServidor = enviarJSON(payload.toString());
                        }

                        if (idServidor > 0) {
                            db.marcarComoSincronizado(idLocal, idServidor);
                            Log.d(TAG, "✅ Reporte " + idLocal + " → servidor id " + idServidor);
                            subidos++;
                        } else {
                            db.marcarErrorSincronizacion(idLocal);
                            errores++;
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error al subir reporte " + idLocal + ": " + e.getMessage());
                        db.marcarErrorSincronizacion(idLocal);
                        errores++;
                    }
                }
            } finally {
                cursor.close();
            }

            final int totalSubidos = subidos;
            final int totalErrores = errores;
            mainHandler.post(() -> {
                if (callback != null) callback.onCompletado(totalSubidos, totalErrores);
                Log.d(TAG, "Sync completo → subidos: " + totalSubidos + " | errores: " + totalErrores);
            });
        });
    }

    // ── Envío multipart con foto real ─────────────────────────────────────────
    private int enviarConFoto(String categoria, String descripcion,
                               double lat, double lng,
                               String direccion, String fecha,
                               File foto) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(ENDPOINT_SYNC);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);

            DataOutputStream out = new DataOutputStream(conn.getOutputStream());

            // Campos de texto
            escribirCampo(out, "categoria",   categoria   != null ? categoria   : "");
            escribirCampo(out, "descripcion", descripcion != null ? descripcion : "");
            escribirCampo(out, "latitud",     String.valueOf(lat));
            escribirCampo(out, "longitud",    String.valueOf(lng));
            escribirCampo(out, "direccion",   direccion   != null ? direccion   : "");
            escribirCampo(out, "fecha",       fecha       != null ? fecha       : "");

            // Archivo de foto
            out.writeBytes("--" + BOUNDARY + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"foto\"; filename=\"" + foto.getName() + "\"\r\n");
            out.writeBytes("Content-Type: image/jpeg\r\n\r\n");

            FileInputStream fis = new FileInputStream(foto);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            fis.close();
            out.writeBytes("\r\n");

            // Cierre
            out.writeBytes("--" + BOUNDARY + "--\r\n");
            out.flush();
            out.close();

            return leerIdReporte(conn);

        } catch (Exception e) {
            Log.e(TAG, "Error multipart: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return -1;
    }

    private void escribirCampo(DataOutputStream out, String nombre, String valor) throws Exception {
        out.writeBytes("--" + BOUNDARY + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + nombre + "\"\r\n\r\n");
        out.writeBytes(valor + "\r\n");
    }

    // ── Envío JSON sin foto ───────────────────────────────────────────────────
    private int enviarJSON(String jsonPayload) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(ENDPOINT_SYNC);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.getOutputStream().write(jsonPayload.getBytes("UTF-8"));
            return leerIdReporte(conn);
        } catch (Exception e) {
            Log.e(TAG, "Error JSON: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return -1;
    }

    private int leerIdReporte(HttpURLConnection conn) throws Exception {
        int status = conn.getResponseCode();
        Log.d(TAG, "HTTP " + status);
        if (status == 200 || status == 201) {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line.trim());
            br.close();
            JSONObject json = new JSONObject(sb.toString());
            return json.optInt("id_reporte", -1);
        }
        return -1;
    }

    public boolean hayInternet() {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && (
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        );
    }

    public interface SyncCallback {
        void onCompletado(int subidos, int errores);
        void onSinInternet();
    }
}
