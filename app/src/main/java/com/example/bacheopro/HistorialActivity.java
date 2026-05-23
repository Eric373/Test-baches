package com.example.bacheopro;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import Dba.DatabaseHelper;

public class HistorialActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historial);

        Button btnVolverInicio = findViewById(R.id.btnVolverInicio);
        LinearLayout contenedorReportes = findViewById(R.id.contenedor_reportes);

        cargarReportes(contenedorReportes);

        btnVolverInicio.setOnClickListener(v -> {
            Intent intent = new Intent(HistorialActivity.this, MapaActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void cargarReportes(LinearLayout contenedor) {
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT * FROM " + DatabaseHelper.TABLE_REPORTES + " ORDER BY " + DatabaseHelper.COL_ID + " DESC", null);

        LayoutInflater inflater = LayoutInflater.from(this);
        contenedor.removeAllViews();

        if (cursor.moveToFirst()) {
            do {
                View itemView = inflater.inflate(R.layout.item_reporte, contenedor, false);

                ImageView ivMiniatura = itemView.findViewById(R.id.iv_miniatura_bache);
                TextView tvTitulo = itemView.findViewById(R.id.tv_titulo_reporte);
                TextView tvFecha = itemView.findViewById(R.id.tv_fecha_reporte);
                TextView tvCategoria = itemView.findViewById(R.id.tv_categoria_reporte);

                String rutaFoto = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RUTA_FOTO));
                String ubicacion = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UBICACION));
                String fecha = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_FECHA));
                String categoria = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CATEGORIA));

                tvTitulo.setText(ubicacion.replace("📍 ", ""));
                tvFecha.setText("Fecha: " + fecha);
                tvCategoria.setText(categoria);

                if (rutaFoto != null) {
                    File imgFile = new File(rutaFoto);
                    if (imgFile.exists()) {
                        Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                        ivMiniatura.setImageBitmap(myBitmap);
                    }
                }

                contenedor.addView(itemView);

            } while (cursor.moveToNext());
        }
        cursor.close();
    }
}