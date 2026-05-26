package com.example.bacheopro;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class HistorialActivity extends AppCompatActivity {

    // Declaramos las variables para controlar el diseño del Rastreador
    private TextView tvTituloRastreador;
    private TextView tvSubtitulo;
    private CardView cvReporteItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Cargamos tu nuevo diseño XML del Rastreador de Folios
        setContentView(R.layout.activity_historial);

        // 2. Enlazamos la lógica de Java con los IDs de tu XML
        tvTituloRastreador = findViewById(R.id.tv_titulo_rastreador);
        tvSubtitulo = findViewById(R.id.tv_subtitulo);
        cvReporteItem = findViewById(R.id.cv_reporte_item);

        // --- ARQUITECTURA DE DATOS FUTURA ---
        /* * Nota de Ingeniería:
         * Tu base de datos SQLite (DatabaseHelper) ya guarda los reportes.
         * En la siguiente fase, crearemos una consulta SQL aquí mismo para
         * leer la ubicación, categoría y el estatus real, y pintar
         * las etiquetas de la línea de tiempo dinámicamente.
         */
    }
}