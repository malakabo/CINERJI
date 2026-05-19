package com.example.moviesapp_aboulethar;
public class MyMovieData {
    private int movieId;
    private String movieName;
    private String movieDate;
    private String movieImage;

    public MyMovieData(int movieId, String movieName, String movieDate, String movieImage) {
        this.movieId = movieId;
        this.movieName = movieName;
        this.movieDate = movieDate;
        this.movieImage = movieImage;
    }

    public int getMovieId() { return movieId; }
    public String getMovieName() { return movieName; }
    public String getMovieDate() { return movieDate; }
    public String getMovieImage() { return movieImage; }
}