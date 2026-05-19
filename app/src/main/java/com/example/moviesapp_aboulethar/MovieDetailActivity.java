package com.example.moviesapp_aboulethar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MovieDetailActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TMDB_API_KEY = "d974791c08c9256d460d869cef30bbe1";
    private static final String GOOGLE_API_KEY = "AIzaSyD0eTvCOIRTD3NnGJEF9dX-Kw8Mb8ZtsBg";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private TextView descriptionTextView, nameTextView;
    private ImageView imgView;
    private String trailerKey;
    private RequestQueue requestQueue;
    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_detail);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Détail du film");
        }

        descriptionTextView = findViewById(R.id.Details);
        nameTextView = findViewById(R.id.textName);
        imgView = findViewById(R.id.imageview);
        requestQueue = Volley.newRequestQueue(this);

        // Deep link (QR code) → moviesapp://movie?id=299534
        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            String movieIdStr = intent.getData().getQueryParameter("id");
            if (movieIdStr != null) {
                fetchMovieDetails(Integer.parseInt(movieIdStr));
            }
        } else {
            // Ouverture normale depuis MainActivity
            int movieId = getIntent().getIntExtra("movieId", -1);
            if (movieId != -1) fetchMovieDetails(movieId);
        }

        Button playButton = findViewById(R.id.playButton);
        playButton.setOnClickListener(v -> playTrailer());

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void fetchMovieDetails(int movieId) {
        String detailsUrl = "https://api.themoviedb.org/3/movie/" + movieId +
                "?api_key=" + TMDB_API_KEY;
        String videosUrl = "https://api.themoviedb.org/3/movie/" + movieId +
                "/videos?api_key=" + TMDB_API_KEY;

        requestQueue.add(new JsonObjectRequest(Request.Method.GET, detailsUrl, null,
                response -> {
                    try {
                        nameTextView.setText(response.getString("title"));
                        descriptionTextView.setText(response.getString("overview"));
                        Glide.with(this)
                                .load("https://image.tmdb.org/t/p/w500" +
                                        response.getString("poster_path"))
                                .into(imgView);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> descriptionTextView.setText("Erreur lors du chargement")
        ));

        requestQueue.add(new JsonObjectRequest(Request.Method.GET, videosUrl, null,
                response -> {
                    try {
                        JSONArray results = response.getJSONArray("results");
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject video = results.getJSONObject(i);
                            if (video.getString("type").equals("Trailer")) {
                                trailerKey = video.getString("key");
                                break;
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> Toast.makeText(this, "Trailer non disponible",
                        Toast.LENGTH_SHORT).show()
        ));
    }

    private void playTrailer() {
        if (trailerKey != null && !trailerKey.isEmpty()) {
            Intent intent = new Intent(this, VideoPlayer.class);
            intent.putExtra("videoUrl", "https://www.youtube.com/embed/" + trailerKey);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Trailer non disponible", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            moveToCurrentLocation();
            searchNearbyCinemas();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void moveToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (loc != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(loc.getLatitude(), loc.getLongitude()), 13));
        } else {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(33.5731, -7.5898), 13));
            Toast.makeText(this, "Position GPS non disponible, affichage Casablanca",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void searchNearbyCinemas() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (loc == null) loc = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);

        double lat = 33.5671701592;
        double lng = -7.64043960923;

        if (loc != null) {
            lat = loc.getLatitude();
            lng = loc.getLongitude();
        }

        double finalLat = lat;
        double finalLng = lng;

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 13));

        String overpassUrl = "https://overpass-api.de/api/interpreter?" +
                "data=[out:json];" +
                "(" +
                "node[amenity=cinema](around:5000," + lat + "," + lng + ");" +
                "way[amenity=cinema](around:5000," + lat + "," + lng + ");" +
                "relation[amenity=cinema](around:5000," + lat + "," + lng + ");" +
                ");out center;";

        Log.d("OVERPASS", "URL: " + overpassUrl);

        com.android.volley.toolbox.StringRequest request =
                new com.android.volley.toolbox.StringRequest(
                        Request.Method.GET, overpassUrl,
                        response -> {
                            try {
                                JSONObject json = new JSONObject(response);
                                JSONArray elements = json.getJSONArray("elements");

                                Log.d("OVERPASS", "Cinémas trouvés: " + elements.length());

                                if (elements.length() == 0) {
                                    Toast.makeText(this,
                                            "Aucun cinéma trouvé dans un rayon de 5km",
                                            Toast.LENGTH_LONG).show();
                                    mMap.addMarker(new MarkerOptions()
                                            .position(new LatLng(finalLat, finalLng))
                                            .title("📍 Votre position"));
                                    return;
                                }

                                for (int i = 0; i < elements.length(); i++) {
                                    JSONObject element = elements.getJSONObject(i);

                                    double cinemaLat, cinemaLng;
                                    String type = element.getString("type");

                                    if (type.equals("node")) {
                                        cinemaLat = element.getDouble("lat");
                                        cinemaLng = element.getDouble("lon");
                                    } else {
                                        JSONObject center = element.optJSONObject("center");
                                        if (center == null) continue;
                                        cinemaLat = center.getDouble("lat");
                                        cinemaLng = center.getDouble("lon");
                                    }

                                    String name = "Cinéma";
                                    JSONObject tags = element.optJSONObject("tags");
                                    if (tags != null) {
                                        if (tags.has("name")) {
                                            name = tags.getString("name");
                                        } else if (tags.has("name:fr")) {
                                            name = tags.getString("name:fr");
                                        } else if (tags.has("name:ar")) {
                                            name = tags.getString("name:ar");
                                        }
                                    }

                                    float[] dist = new float[1];
                                    android.location.Location.distanceBetween(
                                            finalLat, finalLng,
                                            cinemaLat, cinemaLng, dist);
                                    int distMetres = (int) dist[0];

                                    mMap.addMarker(new MarkerOptions()
                                            .position(new LatLng(cinemaLat, cinemaLng))
                                            .title("🎬 " + name)
                                            .snippet("📍 " + distMetres + " m de vous"));

                                    Log.d("OVERPASS", "Cinéma: " + name +
                                            " (" + distMetres + "m)");
                                }

                                Toast.makeText(this,
                                        elements.length() + " cinéma(s) trouvé(s) !",
                                        Toast.LENGTH_SHORT).show();

                            } catch (JSONException e) {
                                e.printStackTrace();
                                Log.e("OVERPASS", "Erreur JSON: " + e.getMessage());
                                Toast.makeText(this, "Erreur parsing",
                                        Toast.LENGTH_SHORT).show();
                            }
                        },
                        error -> {
                            Log.e("OVERPASS", "Erreur réseau: " + error.getMessage());
                            Toast.makeText(this, "Erreur Overpass API",
                                    Toast.LENGTH_SHORT).show();
                        }
                );

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                15000, 1, 1.0f));
        requestQueue.add(request);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (mMap != null) {
                mMap.setMyLocationEnabled(true);
                moveToCurrentLocation();
                searchNearbyCinemas();
            }
        }
    }
}