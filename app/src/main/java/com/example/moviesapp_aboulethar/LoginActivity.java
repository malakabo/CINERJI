package com.example.moviesapp_aboulethar;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private static final String BACKEND_URL = "http://10.0.2.2:8000";

    private EditText editEmail, editPassword;
    private RequestQueue queue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        queue = Volley.newRequestQueue(this);

        Button btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> login());

        TextView tvRegister = findViewById(R.id.tvRegister);
        tvRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class))
        );
    }

    private void login() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Remplis tous les champs", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", password);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    BACKEND_URL + "/login",
                    body,
                    response -> {
                        try {
                            String token = response.getString("access_token");
                            // Sauvegarder le token
                            SharedPreferences prefs = getSharedPreferences("MoviesApp", MODE_PRIVATE);
                            prefs.edit().putString("token", token).apply();
                            prefs.edit().putString("email", email).apply();

                            Toast.makeText(this, "Connexion réussie !", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(this, MainActivity.class));
                            finish();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    },
                    error -> {
                        String errorMsg = "Erreur inconnue";
                        if (error.networkResponse != null) {
                            errorMsg = "Code: " + error.networkResponse.statusCode;
                            try {
                                errorMsg += " - " + new String(error.networkResponse.data);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else if (error.getMessage() != null) {
                            errorMsg = error.getMessage();
                        }
                        String finalMsg = errorMsg;
                        runOnUiThread(() -> Toast.makeText(this, finalMsg, Toast.LENGTH_LONG).show());
                    }
            );
            queue.add(request);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}