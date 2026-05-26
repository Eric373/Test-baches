package com.example.bacheopro;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsuario;
    private EditText etPassword;
    private Button btnIngresar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 1. Enlazamos la interfaz con la lógica
        etUsuario = findViewById(R.id.et_usuario);
        etPassword = findViewById(R.id.et_password);
        btnIngresar = findViewById(R.id.btn_ingresar);

        // 2. Programamos el clic del botón
        btnIngresar.setOnClickListener(v -> {
            String user = etUsuario.getText().toString().trim();
            String pass = etPassword.getText().toString().trim();

            // Validación de seguridad básica
            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Llena todos los campos, por favor", Toast.LENGTH_SHORT).show();
            }
            else if (user.equals("admin") && pass.equals("1234")) {
                // Credenciales correctas
                Toast.makeText(LoginActivity.this, "¡Bienvenido a BacheoPro!", Toast.LENGTH_SHORT).show();

                // SALTO DIRECTO A LA CÁMARA (MainActivity)
                Intent intent = new Intent(LoginActivity.this, MapaActivity.class);
                startActivity(intent);

                // Destruimos la pantalla de Login para que no puedan regresar dándole atrás
                finish();
            } else {
                Toast.makeText(LoginActivity.this, "Credenciales incorrectas", Toast.LENGTH_SHORT).show();
            }
        });
    }
}