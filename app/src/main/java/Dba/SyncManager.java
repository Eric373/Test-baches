package Dba;

import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SyncManager {

    private static final String TAG           = "BacheoPro_Sync";
    private static final String BASE_URL      = "http://192.168.100.24:3000";
    private static final String ENDPOINT_SYNC = BASE_URL + "/api/reportes";
    private static final int    MAX_INTENTOS  = 5;
    private static final int    TIMEOUT_MS    = 10_000;

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
                    String fecha       = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_FECHA));

                    try {
                        JSONObject payload = new JSONObject();
                        payload.put("categoria",   categoria   != null ? categoria   : "");
                        payload.put("descripcion", descripcion != null ? descripcion : "");
                        payload.put("latitud",     lat);
                        payload.put("longitud",    lng);
                        payload.put("url_imagen",  rutaFoto    != null ? rutaFoto    : "");
                        payload.put("fecha",       fecha       != null ? fecha       : "");

                        Log.d(TAG, "Enviando reporte " + idLocal + " | lat=" + lat + " lng=" + lng);

                        int idServidor = enviarAlServidor(payload.toString());

                        if (idServidor > 0) {
                            db.marcarComoSincronizado(idLocal, idServidor);
                            Log.d(TAG, "✅ Reporte " + idLocal + " → servidor id " + idServidor);
                            subidos++;
                        } else {
                            manejarFallo(idLocal, payload.toString());
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

            int reintentados = procesarCola();

            final int totalSubidos = subidos + reintentados;
            final int totalErrores = errores;

            mainHandler.post(() -> {
                if (callback != null) callback.onCompletado(totalSubidos, totalErrores);
                Log.d(TAG, "Sync completo → subidos: " + totalSubidos + " | errores: " + totalErrores);
            });
        });
    }

    private int enviarAlServidor(String jsonPayload) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(ENDPOINT_SYNC);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int statusCode = conn.getResponseCode();
            Log.d(TAG, "HTTP " + statusCode + " para payload: " + jsonPayload);

            if (statusCode == 200 || statusCode == 201) {
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
                );
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line.trim());
                br.close();

                JSONObject respJson = new JSONObject(response.toString());
                return respJson.optInt("id_reporte", -1);
            }

        } catch (Exception e) {
            Log.e(TAG, "HTTP error: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return -1;
    }

    private void manejarFallo(long idLocal, String payload) {
        db.marcarErrorSincronizacion(idLocal);
        db.encolarParaSync(idLocal, payload);
        Log.w(TAG, "⚠️ Reporte " + idLocal + " encolado para reintento.");
    }

    private int procesarCola() {
        int reintentados = 0;
        Cursor cursor = db.obtenerCola();
        try {
            while (cursor.moveToNext()) {
                long   idCola   = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_QUEUE_ID));
                long   idLocal  = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_QUEUE_REPORTE));
                String payload  = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_QUEUE_PAYLOAD));
                int    intentos = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_QUEUE_INTENTOS));

                if (intentos >= MAX_INTENTOS) {
                    Log.w(TAG, "❌ Reporte " + idLocal + " descartado tras " + MAX_INTENTOS + " intentos.");
                    db.eliminarDeCola(idCola);
                    continue;
                }

                db.incrementarIntento(idCola);
                int idServidor = enviarAlServidor(payload);

                if (idServidor > 0) {
                    db.marcarComoSincronizado(idLocal, idServidor);
                    db.eliminarDeCola(idCola);
                    reintentados++;
                    Log.d(TAG, "✅ Reintento exitoso: reporte " + idLocal);
                }
            }
        } finally {
            cursor.close();
        }
        return reintentados;
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
