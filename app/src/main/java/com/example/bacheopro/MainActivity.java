package com.example.bacheopro;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ImageButton btnTomarFoto;
    private Button btnEnviar;
    private ImageView imgVistaPrevia;
    private TextView txtCoordenadas;

    private static final int PERMISSION_REQUEST_CODE = 100;
    private ActivityResultLauncher<Intent> cameraLauncher;

    private FusedLocationProviderClient clienteUbicacion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnTomarFoto = findViewById(R.id.btnTomarFoto);
        btnEnviar = findViewById(R.id.btnEnviar);
        imgVistaPrevia = findViewById(R.id.imgVistaPrevia);
        txtCoordenadas = findViewById(R.id.txtCoordenadas);

        clienteUbicacion = LocationServices.getFusedLocationProviderClient(this);

        // Configurar qué pasa cuando regresamos de la cámara
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Bundle extras = result.getData().getExtras();
                        Bitmap imageBitmap = (Bitmap) extras.get("data");
                        imgVistaPrevia.setImageBitmap(imageBitmap);

                        btnEnviar.setEnabled(true);

                        obtenerCoordenadasReales();
                    }
                }
        );

        btnTomarFoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkAndRequestPermissions()) {
                    openCamera();
                }
            }
        });

        btnEnviar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "¡Evidencia guardada temporalmente!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(MainActivity.this, HistorialActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(takePictureIntent);
    }

    // Método para extraer latitud y longitud
    @SuppressLint("MissingPermission")
    private void obtenerCoordenadasReales() {
        txtCoordenadas.setText("GPS: Calculando posición...");

        // Como ya pedimos permisos antes de abrir la cámara, podemos leer el GPS
        clienteUbicacion.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    double latitud = location.getLatitude();
                    double longitud = location.getLongitude();

                    // Formatear a 5 decimales para que el texto no sea gigante
                    String textoGps = String.format("Coordenadas: %.5f, %.5f", latitud, longitud);
                    txtCoordenadas.setText(textoGps);
                    txtCoordenadas.setTextColor(android.graphics.Color.parseColor("#4CAF50")); // Cambia a verde
                } else {
                    txtCoordenadas.setText("GPS: No se pudo obtener señal.");
                    txtCoordenadas.setTextColor(android.graphics.Color.parseColor("#F44336")); // Cambia a rojo
                }
            }
        });
    }

    private boolean checkAndRequestPermissions() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
        };

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(perm);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Se requieren permisos de Cámara y GPS para reportar.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}