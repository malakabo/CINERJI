package com.example.moviesapp_aboulethar;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    // CORRECT pour l'émulateur
    private static final String BACKEND_URL = "http://10.0.2.2:8000";
    private static final int CAMERA_REQUEST_CODE = 300;
    private static final int CAMERA_PERMISSION_CODE = 301;

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private EditText editMessage;
    private RequestQueue queue;
    private Uri photoUri;
    private boolean isMinor = false;
    private boolean ageDetected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        editMessage = findViewById(R.id.editMessage);
        queue = Volley.newRequestQueue(this);

        chatAdapter = new ChatAdapter(messages);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(chatAdapter);

        addMessage("Bonjour ! Je suis ton assistant films 🎬\n\nClique sur 📷 pour que je détecte ton âge et je t'afficherai les films adaptés !", false);

        findViewById(R.id.btnSend).setOnClickListener(v -> sendMessage());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCamera).setOnClickListener(v -> openCamera());
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
            return;
        }
        try {
            File photoFile = new File(
                    getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "face_" + System.currentTimeMillis() + ".jpg"
            );
            photoUri = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    photoFile
            );
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            startActivityForResult(intent, CAMERA_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Erreur caméra: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void detectAge(Bitmap bitmap) {
        addMessage("📷 Analyse de ton visage en cours...", false);

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] imageBytes = baos.toByteArray();
        String base64Image = android.util.Base64.encodeToString(
                imageBytes, android.util.Base64.DEFAULT);

        try {
            JSONObject body = new JSONObject();
            body.put("image", base64Image);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    BACKEND_URL + "/detect-age",
                    body,
                    response -> {
                        try {
                            int age = response.getInt("age");
                            isMinor = response.getBoolean("is_minor");
                            ageDetected = true;

                            if (isMinor) {
                                addMessage("👶 Âge estimé : " + age + " ans\nTu es mineur ! Je vais te recommander des films adaptés 🎬", false);
                                askAI("Recommande des films pour enfants et adolescents de " + age + " ans, films familiaux et animations uniquement, sans violence ni contenu adulte");
                            } else {
                                addMessage("🎬 Âge estimé : " + age + " ans\nTu es majeur ! Tu as accès à tous les films !", false);
                                askAI("Recommande des films populaires pour un adulte de " + age + " ans");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            addMessage("❌ Erreur de parsing", false);
                        }
                    },
                    error -> {
                        String msg = "❌ Erreur de détection";
                        if (error.networkResponse != null) {
                            try {
                                msg += ": " + new String(error.networkResponse.data, "UTF-8");
                            } catch (Exception ignored) {}
                        }
                        addMessage(msg, false);
                    }
            );
            request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                    60000, 0, 1.0f
            ));
            queue.add(request);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String userMsg = editMessage.getText().toString().trim();
        if (userMsg.isEmpty()) return;

        addMessage(userMsg, true);
        editMessage.setText("");

        String prompt;
        if (isMinor) {
            prompt = "Tu es un assistant films pour enfants et adolescents. " +
                    "Recommande UNIQUEMENT des films familiaux, animations, aventure sans violence ni contenu adulte. " +
                    "Réponds en français. Question: " + userMsg;
        } else {
            prompt = "Tu es un assistant films. Réponds en français de manière courte. " +
                    "Question: " + userMsg;
        }

        askAI(prompt);
    }

    private void askAI(String prompt) {
        addMessage("⏳ En train de réfléchir...", false);

        try {
            JSONObject body = new JSONObject();
            body.put("message", prompt);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    BACKEND_URL + "/chat",
                    body,
                    response -> {
                        try {
                            messages.remove(messages.size() - 1);
                            String aiResponse = response.getString("response");
                            addMessage(aiResponse, false);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    },
                    error -> {
                        messages.remove(messages.size() - 1);
                        addMessage("❌ Erreur de connexion", false);
                        chatAdapter.notifyDataSetChanged();
                    }
            );
            request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                    60000, 0, 1.0f
            ));
            queue.add(request);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void addMessage(String text, boolean isUser) {
        messages.add(new ChatMessage(text, isUser));
        chatAdapter.notifyDataSetChanged();
        chatRecyclerView.scrollToPosition(messages.size() - 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            try {
                // Lire depuis photoUri (pas depuis data.getExtras())
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                        getContentResolver(), photoUri);
                if (bitmap != null) detectAge(bitmap);
            } catch (Exception e) {
                // Si photoUri échoue, essayer depuis data
                if (data != null && data.getExtras() != null) {
                    Bitmap photo = (Bitmap) data.getExtras().get("data");
                    if (photo != null) detectAge(photo);
                } else {
                    Toast.makeText(this, "Erreur lecture photo", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        }
    }
}