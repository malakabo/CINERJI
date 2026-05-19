package com.example.moviesapp_aboulethar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TMDB_API_KEY = "d974791c08c9256d460d869cef30bbe1";
    private static final String URL_POPULAR  = "https://api.themoviedb.org/3/movie/popular";
    private static final String URL_TRENDING = "https://api.themoviedb.org/3/trending/movie/week";
    private static final String URL_TOP      = "https://api.themoviedb.org/3/movie/top_rated";
    private static final String URL_SEARCH   = "https://api.themoviedb.org/3/search/movie";
    private static final int SPEECH_REQUEST_CODE = 100;
    private static final int CAMERA_REQUEST_CODE = 200;

    private RecyclerView recyclerView;
    private MyMovieAdapter myMovieAdapter;
    private EditText searchEditText;
    private TextView textPage;
    private RequestQueue queue;
    private Uri photoUri;

    private int currentPage = 1;
    private String currentUrl = URL_POPULAR;
    private List<MyMovieData> allMovies = new ArrayList<>();
    private boolean isSearchMode = false;
    private Handler searchHandler = new Handler();
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchEditText = findViewById(R.id.editTextSearch);
        recyclerView   = findViewById(R.id.recyclerView);
        textPage       = findViewById(R.id.textPage);
        queue          = Volley.newRequestQueue(this);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        fetchMovies();

        // ── Bouton Déconnexion ──────────────────────────────────────
        Button btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            getSharedPreferences("MoviesApp", MODE_PRIVATE)
                    .edit().clear().apply();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // ── Catégories ──────────────────────────────────────────────
        Button btnPopular  = findViewById(R.id.btnPopular);
        Button btnTrending = findViewById(R.id.btnTrending);
        Button btnTopRated = findViewById(R.id.btnTopRated);

        btnPopular.setOnClickListener(v -> {
            currentUrl = URL_POPULAR;
            currentPage = 1;
            isSearchMode = false;
            searchEditText.setText("");
            resetAndFetch();
            btnPopular.setBackgroundColor(0xFFE50914);
            btnTrending.setBackgroundColor(0xFF1E1E2E);
            btnTopRated.setBackgroundColor(0xFF1E1E2E);
        });

        btnTrending.setOnClickListener(v -> {
            currentUrl = URL_TRENDING;
            currentPage = 1;
            isSearchMode = false;
            searchEditText.setText("");
            resetAndFetch();
            btnPopular.setBackgroundColor(0xFF1E1E2E);
            btnTrending.setBackgroundColor(0xFFE50914);
            btnTopRated.setBackgroundColor(0xFF1E1E2E);
        });

        btnTopRated.setOnClickListener(v -> {
            currentUrl = URL_TOP;
            currentPage = 1;
            isSearchMode = false;
            searchEditText.setText("");
            resetAndFetch();
            btnPopular.setBackgroundColor(0xFF1E1E2E);
            btnTrending.setBackgroundColor(0xFF1E1E2E);
            btnTopRated.setBackgroundColor(0xFFE50914);
        });

        // ── Pagination ──────────────────────────────────────────────
        findViewById(R.id.btnNext).setOnClickListener(v -> {
            if (!isSearchMode) {
                currentPage++;
                fetchMovies();
                recyclerView.scrollToPosition(0);
            }
        });

        findViewById(R.id.btnPrev).setOnClickListener(v -> {
            if (!isSearchMode && currentPage > 1) {
                currentPage--;
                fetchMovies();
                recyclerView.scrollToPosition(0);
            }
        });

        // ── Recherche en temps réel ─────────────────────────────────
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacks(searchRunnable);

                if (s.length() == 0) {
                    isSearchMode = false;
                    resetAndFetch();
                } else if (s.length() >= 2) {
                    searchRunnable = () -> {
                        isSearchMode = true;
                        searchTMDB(s.toString());
                    };
                    searchHandler.postDelayed(searchRunnable, 800);
                }
            }

            @Override public void afterTextChanged(Editable s) {}
        });

        // ── Bouton Recherche ────────────────────────────────────────
        findViewById(R.id.buttonSearch).setOnClickListener(v -> {
            String query = searchEditText.getText().toString().trim();
            if (!query.isEmpty()) {
                isSearchMode = true;
                searchTMDB(query);
            } else {
                isSearchMode = false;
                resetAndFetch();
            }
        });

        // ── Bouton Micro ────────────────────────────────────────────
        Button buttonMic = findViewById(R.id.buttonMic);
        buttonMic.setOnClickListener(v -> startVoiceSearch());

        // ── Bouton Scanner QR ───────────────────────────────────────
        Button btnScan = findViewById(R.id.btnScan);
        btnScan.setOnClickListener(v -> openCamera());

        // ── Bouton Chat IA ──────────────────────────────────────────
        Button btnChat = findViewById(R.id.btnChat);
        btnChat.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ChatActivity.class))
        );

        // ── Infinite scroll ─────────────────────────────────────────
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (!isSearchMode) {
                    LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                    if (lm != null &&
                            lm.findLastVisibleItemPosition() >= lm.getItemCount() - 3) {
                        currentPage++;
                        fetchMovies();
                    }
                }
            }
        });
    }

    // ── Recherche TMDB directe ──────────────────────────────────────
    private void searchTMDB(String query) {
        if (query.isEmpty()) return;

        String url = URL_SEARCH + "?api_key=" + TMDB_API_KEY +
                "&query=" + Uri.encode(query) +
                "&language=fr-FR&page=1";

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray results = response.getJSONArray("results");

                        if (results.length() == 0) {
                            Toast.makeText(this,
                                    "Aucun film trouvé pour : " + query,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        List<MyMovieData> searchResults = new ArrayList<>();
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject obj = results.getJSONObject(i);
                            searchResults.add(new MyMovieData(
                                    obj.getInt("id"),
                                    obj.getString("title"),
                                    obj.optString("release_date", ""),
                                    obj.optString("poster_path", "")
                            ));
                        }

                        MyMovieData[] moviesArray =
                                searchResults.toArray(new MyMovieData[0]);

                        runOnUiThread(() -> {
                            if (myMovieAdapter == null) {
                                myMovieAdapter = new MyMovieAdapter(
                                        moviesArray, MainActivity.this);
                                recyclerView.setAdapter(myMovieAdapter);
                            } else {
                                myMovieAdapter.updateData(moviesArray);
                            }
                            recyclerView.scrollToPosition(0);
                            textPage.setText(results.length() +
                                    " résultats pour \"" + query + "\"");
                        });

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    Log.e("TMDB_SEARCH", "Erreur: " + error.getMessage());
                    Toast.makeText(this, "Erreur de recherche", Toast.LENGTH_SHORT).show();
                }
        );
        queue.add(request);
    }

    // ── Recherche vocale ────────────────────────────────────────────
    private void startVoiceSearch() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Dis le nom d'un film...");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        try {
            List activities = getPackageManager().queryIntentActivities(intent, 0);
            if (activities.size() != 0) {
                startActivityForResult(intent, SPEECH_REQUEST_CODE);
            } else {
                Toast.makeText(this, "Reconnaissance vocale non disponible",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Erreur: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ── Caméra / Scanner QR ─────────────────────────────────────────
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA},
                    CAMERA_REQUEST_CODE);
            return;
        }
        try {
            File photoFile = new File(
                    getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),
                    "qr_" + System.currentTimeMillis() + ".jpg"
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
            Toast.makeText(this, "Erreur: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void scanQRCode(Bitmap bitmap) {
        com.google.mlkit.vision.common.InputImage image =
                com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0);

        com.google.mlkit.vision.barcode.BarcodeScannerOptions options =
                new com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                        .build();

        com.google.mlkit.vision.barcode.BarcodeScanning.getClient(options)
                .process(image)
                .addOnSuccessListener(barcodes -> {
                    if (barcodes.isEmpty()) {
                        Toast.makeText(this, "Aucun QR code détecté, réessaie !",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    for (com.google.mlkit.vision.barcode.common.Barcode barcode : barcodes) {
                        String rawValue = barcode.getRawValue();
                        if (rawValue != null && !rawValue.isEmpty()) {
                            searchEditText.setText(rawValue);
                            searchEditText.setSelection(rawValue.length());
                            isSearchMode = true;
                            searchTMDB(rawValue);
                            Toast.makeText(this, "Recherche : " + rawValue,
                                    Toast.LENGTH_SHORT).show();
                            break;
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Erreur scan: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Permission caméra refusée",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Recherche vocale
        if (requestCode == SPEECH_REQUEST_CODE &&
                resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0);
                searchEditText.setText(spokenText);
                searchEditText.setSelection(spokenText.length());
                isSearchMode = true;
                searchTMDB(spokenText);
                Toast.makeText(this, "Recherche : " + spokenText,
                        Toast.LENGTH_SHORT).show();
            }
        }

        // Scanner QR
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            try {
                Bitmap photo = MediaStore.Images.Media
                        .getBitmap(getContentResolver(), photoUri);
                if (photo != null) scanQRCode(photo);
            } catch (Exception e) {
                Toast.makeText(this, "Erreur: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ── Chargement des films ────────────────────────────────────────
    private void resetAndFetch() {
        allMovies.clear();
        fetchMovies();
    }

    private void fetchMovies() {
        allMovies.clear();
        // Charger 3 pages = 60 films
        for (int p = 1; p <= 3; p++) {
            fetchPage(p);
        }
    }

    private void fetchPage(int page) {
        String url = currentUrl + "?api_key=" + TMDB_API_KEY + "&page=" + page;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray results = response.getJSONArray("results");
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject obj = results.getJSONObject(i);
                            allMovies.add(new MyMovieData(
                                    obj.getInt("id"),
                                    obj.getString("title"),
                                    obj.optString("release_date",
                                            obj.optString("first_air_date", "")),
                                    obj.optString("poster_path", "")
                            ));
                        }

                        MyMovieData[] moviesArray = allMovies.toArray(new MyMovieData[0]);

                        runOnUiThread(() -> {
                            if (myMovieAdapter == null) {
                                myMovieAdapter = new MyMovieAdapter(
                                        moviesArray, MainActivity.this);
                                recyclerView.setAdapter(myMovieAdapter);
                            } else {
                                myMovieAdapter.updateData(moviesArray);
                            }
                            textPage.setText(allMovies.size() + " films");
                        });

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> Log.e("TMDB", "Erreur page " + page + ": " + error.getMessage())
        );
        queue.add(request);
    }
}