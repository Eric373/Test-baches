package Dba;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME    = "BacheoPro.db";
    private static final int    DATABASE_VERSION = 3; // subimos a 3

    public static final String TABLE_REPORTES    = "reportes";
    public static final String COL_ID            = "id";
    public static final String COL_CATEGORIA     = "categoria";
    public static final String COL_DESCRIPCION   = "descripcion";
    public static final String COL_UBICACION     = "ubicacion";   // texto "📍 Ricardo Calva..."
    public static final String COL_LATITUD       = "latitud";     // double
    public static final String COL_LONGITUD      = "longitud";    // double
    public static final String COL_RUTA_FOTO     = "ruta_foto";
    public static final String COL_FECHA         = "fecha";
    public static final String COL_SERVER_ID     = "server_id";
    public static final String COL_SYNC_STATUS   = "sync_status"; // 0=pendiente,1=ok,2=error

    public static final String TABLE_SYNC_QUEUE    = "sync_queue";
    public static final String COL_QUEUE_ID        = "id";
    public static final String COL_QUEUE_REPORTE   = "id_reporte_local";
    public static final String COL_QUEUE_PAYLOAD   = "json_payload";
    public static final String COL_QUEUE_INTENTOS  = "intentos";
    public static final String COL_QUEUE_CREADO    = "creado_en";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE_REPORTES + " (" +
                        COL_ID          + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_CATEGORIA   + " TEXT, "                +
                        COL_DESCRIPCION + " TEXT, "                +
                        COL_UBICACION   + " TEXT, "                +
                        COL_LATITUD     + " REAL DEFAULT 0.0, "    +
                        COL_LONGITUD    + " REAL DEFAULT 0.0, "    +
                        COL_RUTA_FOTO   + " TEXT, "                +
                        COL_FECHA       + " TEXT, "                +
                        COL_SERVER_ID   + " INTEGER DEFAULT NULL, "+
                        COL_SYNC_STATUS + " INTEGER DEFAULT 0"     +
                        ")"
        );
        db.execSQL(
                "CREATE TABLE " + TABLE_SYNC_QUEUE + " (" +
                        COL_QUEUE_ID       + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_QUEUE_REPORTE  + " INTEGER NOT NULL, " +
                        COL_QUEUE_PAYLOAD  + " TEXT NOT NULL, "    +
                        COL_QUEUE_INTENTOS + " INTEGER DEFAULT 0, "+
                        COL_QUEUE_CREADO   + " TEXT DEFAULT (datetime('now'))" +
                        ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Migración desde v1 o v2: añadir columnas lat/lng sin borrar datos
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_REPORTES + " ADD COLUMN " + COL_SERVER_ID   + " INTEGER DEFAULT NULL");
            db.execSQL("ALTER TABLE " + TABLE_REPORTES + " ADD COLUMN " + COL_SYNC_STATUS + " INTEGER DEFAULT 0");
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SYNC_QUEUE + " (" +
                    COL_QUEUE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_QUEUE_REPORTE + " INTEGER NOT NULL, " +
                    COL_QUEUE_PAYLOAD + " TEXT NOT NULL, " +
                    COL_QUEUE_INTENTOS + " INTEGER DEFAULT 0, " +
                    COL_QUEUE_CREADO + " TEXT DEFAULT (datetime('now')))"
            );
        }
        if (oldVersion < 3) {
            // Añadir columnas de lat/lng a registros existentes (quedan en 0.0)
            try { db.execSQL("ALTER TABLE " + TABLE_REPORTES + " ADD COLUMN " + COL_LATITUD  + " REAL DEFAULT 0.0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_REPORTES + " ADD COLUMN " + COL_LONGITUD + " REAL DEFAULT 0.0"); } catch (Exception ignored) {}
        }
    }

    // ── Insertar reporte con lat/lng ──────────────────────────────────────────
    public long insertarReporte(String categoria, String descripcion,
                                String ubicacionTexto, double latitud, double longitud,
                                String rutaFoto, String fecha) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COL_CATEGORIA,   categoria);
        v.put(COL_DESCRIPCION, descripcion);
        v.put(COL_UBICACION,   ubicacionTexto);
        v.put(COL_LATITUD,     latitud);
        v.put(COL_LONGITUD,    longitud);
        v.put(COL_RUTA_FOTO,   rutaFoto);
        v.put(COL_FECHA,       fecha);
        v.put(COL_SYNC_STATUS, 0);
        return db.insert(TABLE_REPORTES, null, v);
    }

    public Cursor obtenerPendientesSincronizacion() {
        return getReadableDatabase().query(
                TABLE_REPORTES, null,
                COL_SYNC_STATUS + " = 0",
                null, null, null, COL_FECHA + " ASC"
        );
    }

    public void marcarComoSincronizado(long idLocal, int idServidor) {
        ContentValues v = new ContentValues();
        v.put(COL_SERVER_ID,   idServidor);
        v.put(COL_SYNC_STATUS, 1);
        getWritableDatabase().update(TABLE_REPORTES, v, COL_ID + "=?",
                new String[]{ String.valueOf(idLocal) });
    }

    public void marcarErrorSincronizacion(long idLocal) {
        ContentValues v = new ContentValues();
        v.put(COL_SYNC_STATUS, 2);
        getWritableDatabase().update(TABLE_REPORTES, v, COL_ID + "=?",
                new String[]{ String.valueOf(idLocal) });
    }

    public void encolarParaSync(long idReporteLocal, String jsonPayload) {
        ContentValues v = new ContentValues();
        v.put(COL_QUEUE_REPORTE,  idReporteLocal);
        v.put(COL_QUEUE_PAYLOAD,  jsonPayload);
        v.put(COL_QUEUE_INTENTOS, 0);
        getWritableDatabase().insert(TABLE_SYNC_QUEUE, null, v);
    }

    public Cursor obtenerCola() {
        return getReadableDatabase().query(TABLE_SYNC_QUEUE, null, null, null, null, null,
                COL_QUEUE_CREADO + " ASC");
    }

    public void incrementarIntento(long idCola) {
        getWritableDatabase().execSQL(
                "UPDATE " + TABLE_SYNC_QUEUE + " SET " + COL_QUEUE_INTENTOS + " = " +
                        COL_QUEUE_INTENTOS + " + 1 WHERE " + COL_QUEUE_ID + " = " + idCola
        );
    }

    public void eliminarDeCola(long idCola) {
        getWritableDatabase().delete(TABLE_SYNC_QUEUE, COL_QUEUE_ID + "=?",
                new String[]{ String.valueOf(idCola) });
    }
}







