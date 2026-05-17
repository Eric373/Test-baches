package com.example.bacheopro;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnSuccessListener;

public class MapaActivity extends AppCompatActivity implements OnMapReadyCallback {

    private Button btnIrAReporte, btnIrAHistorial;
    private GoogleMap miMapa;

    // Herramienta para leer el GPS del teléfono
    private FusedLocationProviderClient clienteUbicacion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mapa);

        btnIrAReporte = findViewById(R.id.btnIrAReporte);
        btnIrAHistorial = findViewById(R.id.btnIrAHistorial);

        // Inicializar el lector de GPS
        clienteUbicacion = LocationServices.getFusedLocationProviderClient(this);

        // Cargar el fragmento del mapa
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapaReal);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        btnIrAReporte.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MapaActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });

        btnIrAHistorial.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MapaActivity.this, HistorialActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        miMapa = googleMap;
        miMapa.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        // 1. VERIFICAR PERMISOS DE GPS
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Si no hay permisos, pedirlos
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        // 2. ACTIVAR EL PUNTO AZUL DE "MI UBICACIÓN" EN EL MAPA
        miMapa.setMyLocationEnabled(true);

        // 3. BUSCAR LA UBICACIÓN EXACTA Y MOVER LA CÁMARA AHÍ
        clienteUbicacion.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    // Si encontró el GPS, hacer zoom en esa calle (Nivel de zoom 17f)
                    LatLng miUbicacionExacta = new LatLng(location.getLatitude(), location.getLongitude());
                    miMapa.moveCamera(CameraUpdateFactory.newLatLngZoom(miUbicacionExacta, 17f));
                } else {
                    // Si el GPS tarda en reaccionar, poner Ixtapaluca por defecto temporalmente
                    LatLng ubicacionDefecto = new LatLng(19.316, -98.883);
                    miMapa.moveCamera(CameraUpdateFactory.newLatLngZoom(ubicacionDefecto, 13f));
                }
            }
        });
    }
}