package Dba;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "BacheoPro.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_REPORTES = "reportes";
    public static final String COL_ID = "id";
    public static final String COL_CATEGORIA = "categoria";
    public static final String COL_DESCRIPCION = "descripcion";
    public static final String COL_UBICACION = "ubicacion";
    public static final String COL_RUTA_FOTO = "ruta_foto";
    public static final String COL_FECHA = "fecha";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_REPORTES + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_CATEGORIA + " TEXT, " +
                COL_DESCRIPCION + " TEXT, " +
                COL_UBICACION + " TEXT, " +
                COL_RUTA_FOTO + " TEXT, " +
                COL_FECHA + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_REPORTES);
        onCreate(db);
    }

    public long insertarReporte(String categoria, String descripcion, String ubicacion, String rutaFoto, String fecha) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_CATEGORIA, categoria);
        values.put(COL_DESCRIPCION, descripcion);
        values.put(COL_UBICACION, ubicacion);
        values.put(COL_RUTA_FOTO, rutaFoto);
        values.put(COL_FECHA, fecha);
        return db.insert(TABLE_REPORTES, null, values);
    }
}