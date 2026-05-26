package com.example.bacheopro;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import Dba.DatabaseHelper;
import Dba.SyncManager;

public class MainActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private ImageView ivPhotoPreview;
    private LinearLayout layoutPreviewActions;
    private ImageButton btnTakePhoto;
    private Button btnRetake, btnConfirmPhoto;

    private ImageCapture imageCapture;
    private String rutaFotoActual = null;
    private boolean fotoConfirmada = false;

    private TextView tvCoordenadas;
    private EditText etDescripcion;
    private Spinner spCategoria;

    private FusedLocationProviderClient fusedLocationClient;
    private double latitudActual = 19.32266;
    private double longitudActual = -98.91540;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean cameraGranted = result.getOrDefault(Manifest.permission.CAMERA, false);
                Boolean locationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);

                if (cameraGranted != null && cameraGranted) {
                    iniciarCamara();
                } else {
                    Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
                }

                if (locationGranted != null && locationGranted) {
                    obtenerUbicacionGPS();
                } else {
                    Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);
        spCategoria = findViewById(R.id.sp_categoria);
        etDescripcion = findViewById(R.id.et_descripcion);
        Button btnFinalizarReporte = findViewById(R.id.btnFinalizarReporte);
        btnTakePhoto = findViewById(R.id.btn_take_photo);
        tvCoordenadas = findViewById(R.id.tv_coordenadas);

        ivPhotoPreview = findViewById(R.id.iv_photo_preview);
        layoutPreviewActions = findViewById(R.id.layout_preview_actions);
        btnRetake = findViewById(R.id.btn_retake);
        btnConfirmPhoto = findViewById(R.id.btn_confirm_photo);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        String[] categorias = {"Selecciona una categoría", "Bache profundo", "Socavón", "Grieta extensa", "Falta de tapa/coladera"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, categorias) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setTextSize(16f);
                return tv;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"));
                tv.setPadding(40, 40, 40, 40);
                return tv;
            }
        };
        spCategoria.setAdapter(adapter);

        revisarPermisos();

        btnTakePhoto.setOnClickListener(v -> tomarFoto());
        btnRetake.setOnClickListener(v -> resetCamara());
        btnConfirmPhoto.setOnClickListener(v -> confirmarFoto());
        btnFinalizarReporte.setOnClickListener(v -> finalizarFlujoReporte());
    }


    @Override
    protected void onResume() {
        super.onResume();

        SyncManager sync = new SyncManager(this);
        sync.sincronizarPendientes(new SyncManager.SyncCallback() {
            @Override
            public void onCompletado(int subidos, int errores) {
                if (subidos > 0) {
                    Toast.makeText(MainActivity.this,
                            subidos + " reportes sincronizados ✓",
                            Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onSinInternet() {
                // silencioso — no molestes al usuario si no hay internet
            }
        });
    }


    private void revisarPermisos() {
        String[] permisos = {Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION};
        boolean todosOtorgados = true;

        for (String permiso : permisos) {
            if (ContextCompat.checkSelfPermission(this, permiso) != PackageManager.PERMISSION_GRANTED) {
                todosOtorgados = false;
                break;
            }
        }

        if (todosOtorgados) {
            iniciarCamara();
            obtenerUbicacionGPS();
        } else {
            requestPermissionsLauncher.launch(permisos);
        }
    }

    private void obtenerUbicacionGPS() {
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            latitudActual = location.getLatitude();
                            longitudActual = location.getLongitude();
                            obtenerDireccion(latitudActual, longitudActual);
                        } else {
                            tvCoordenadas.setText("📍 Ubicación no disponible");
                        }
                    });
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void obtenerDireccion(double lat, double lon) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> direcciones = geocoder.getFromLocation(lat, lon, 1);
            if (direcciones != null && !direcciones.isEmpty()) {
                Address direccion = direcciones.get(0);
                String calle = direccion.getThoroughfare();
                String numero = direccion.getSubThoroughfare();
                String colonia = direccion.getSubLocality();

                StringBuilder direccionCompleta = new StringBuilder("📍 ");
                if (calle != null) direccionCompleta.append(calle).append(" ");
                if (numero != null) direccionCompleta.append(numero).append(", ");
                if (colonia != null) direccionCompleta.append(colonia);

                if (direccionCompleta.toString().trim().equals("📍")) {
                    direccionCompleta = new StringBuilder("📍 ").append(direccion.getAddressLine(0));
                }
                tvCoordenadas.setText(direccionCompleta.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
            tvCoordenadas.setText("📍 " + lat + ", " + lon);
        }
    }

    private void iniciarCamara() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void tomarFoto() {
        if (imageCapture == null) return;

        File photoFile = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Bache_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                rutaFotoActual = photoFile.getAbsolutePath();
                mostrarVistaPrevia(photoFile);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(MainActivity.this, "Error al capturar evidencia", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void mostrarVistaPrevia(File photoFile) {
        runOnUiThread(() -> {
            viewFinder.setVisibility(View.GONE);
            ivPhotoPreview.setImageURI(Uri.fromFile(photoFile));
            ivPhotoPreview.setVisibility(View.VISIBLE);

            btnTakePhoto.setVisibility(View.GONE);
            layoutPreviewActions.setVisibility(View.VISIBLE);
        });
    }

    private void resetCamara() {
        runOnUiThread(() -> {
            if (rutaFotoActual != null) {
                File file = new File(rutaFotoActual);
                if (file.exists()) {
                    file.delete();
                }
                rutaFotoActual = null;
            }
            fotoConfirmada = false;
            ivPhotoPreview.setVisibility(View.GONE);
            viewFinder.setVisibility(View.VISIBLE);
            layoutPreviewActions.setVisibility(View.GONE);
            btnTakePhoto.setVisibility(View.VISIBLE);
        });
    }

    private void confirmarFoto() {
        runOnUiThread(() -> {
            fotoConfirmada = true;
            layoutPreviewActions.setVisibility(View.GONE);
            Toast.makeText(MainActivity.this, "Evidencia vinculada", Toast.LENGTH_SHORT).show();
        });
    }

    private void finalizarFlujoReporte() {
        if (rutaFotoActual == null || !fotoConfirmada) {
            Toast.makeText(this, "Debes capturar y confirmar la foto", Toast.LENGTH_LONG).show();
            return;
        }
        if (spCategoria.getSelectedItemPosition() == 0) {
            Toast.makeText(this, "Selecciona una categoría", Toast.LENGTH_SHORT).show();
            return;
        }

        String categoria       = spCategoria.getSelectedItem().toString();
        String descripcion     = etDescripcion.getText().toString();
        String ubicacionTexto  = tvCoordenadas.getText().toString(); // "📍 Ricardo Calva 11..."
        String fechaHoraActual = new SimpleDateFormat("dd MMM yyyy, HH:mm",
                Locale.getDefault()).format(new Date());

        // ✅ Ahora guardamos lat/lng numéricos + texto de dirección
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        long id = dbHelper.insertarReporte(
                categoria,
                descripcion,
                ubicacionTexto,   // dirección legible para mostrar en historial
                latitudActual,    // 19.32266  ← variable que ya tienes en MainActivity
                longitudActual,   // -98.91540 ← variable que ya tienes en MainActivity
                rutaFotoActual,
                fechaHoraActual
        );

        if (id != -1) {
            Toast.makeText(this, "Reporte guardado", Toast.LENGTH_SHORT).show();

            // Intentar sincronizar inmediatamente si hay internet
            SyncManager sync = new SyncManager(this);
            sync.sincronizarPendientes();
        }

        Intent intent = new Intent(this, HistorialActivity.class);
        intent.putExtra("RUTA_FOTO",  rutaFotoActual);
        intent.putExtra("FECHA_HORA", fechaHoraActual);
        intent.putExtra("UBICACION",  ubicacionTexto);
        startActivity(intent);
        finish();
    }

}