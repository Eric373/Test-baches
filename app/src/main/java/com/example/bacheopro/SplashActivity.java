package com.example.bacheopro;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hacemos que la pantalla sea de pantalla completa (sin barra de estado)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_splash);

        // Retrasamos 2 segundos (2000 ms) antes de ir a MapaActivity
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Iniciamos la actividad principal
                Intent intent = new Intent(SplashActivity.this, MapaActivity.class);
                startActivity(intent);
                // Cerramos la actividad de carga
                finish();
            }
        }, 2000); // 2000 milisegundos = 2 segundos
    }
}