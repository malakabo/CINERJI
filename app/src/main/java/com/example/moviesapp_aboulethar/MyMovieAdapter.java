package com.example.moviesapp_aboulethar;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MyMovieAdapter extends RecyclerView.Adapter<MyMovieAdapter.ViewHolder> implements Filterable {

    private MyMovieData[] originalMovieData;
    private List<MyMovieData> filteredMovieData;
    private final Context context;
    private int lastPosition = -1;

    public MyMovieAdapter(MyMovieData[] myMovieData, Context context) {
        this.originalMovieData = myMovieData;
        this.filteredMovieData = new ArrayList<>(Arrays.asList(myMovieData));
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.movie_item_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final MyMovieData movie = filteredMovieData.get(position);
        holder.textViewName.setText(movie.getMovieName());
        holder.textViewDate.setText(String.format("📅 %s", movie.getMovieDate()));

        Glide.with(context)
                .load("https://image.tmdb.org/t/p/w500" + movie.getMovieImage())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.movieImage);

        // Animation à l'entrée
        if (position > lastPosition) {
            holder.itemView.setAlpha(0f);
            holder.itemView.setTranslationY(50f);
            holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .start();
            lastPosition = position;
        }

        // Click listener corrigé pour éviter les erreurs de position
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                MyMovieData clickedMovie = filteredMovieData.get(pos);
                Intent intent = new Intent(context, MovieDetailActivity.class);
                intent.putExtra("movieId", clickedMovie.getMovieId());
                context.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() { return filteredMovieData.size(); }

    public void updateData(MyMovieData[] newData) {
        this.originalMovieData = newData;
        this.filteredMovieData = new ArrayList<>(Arrays.asList(newData));
        this.lastPosition = -1;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView movieImage;
        TextView textViewName, textViewDate;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            movieImage = itemView.findViewById(R.id.imageview);
            textViewName = itemView.findViewById(R.id.textName);
            textViewDate = itemView.findViewById(R.id.textdate);
        }
    }

    @Override
    public Filter getFilter() { return movieFilter; }

    private final Filter movieFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<MyMovieData> filteredList = new ArrayList<>();
            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(Arrays.asList(originalMovieData));
            } else {
                String pattern = constraint.toString().toLowerCase().trim();
                for (MyMovieData movie : originalMovieData) {
                    if (movie.getMovieName().toLowerCase().contains(pattern)) {
                        filteredList.add(movie);
                    }
                }
            }
            FilterResults results = new FilterResults();
            results.values = filteredList;
            return results;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filteredMovieData.clear();
            if (results.values != null) {
                filteredMovieData.addAll((List<MyMovieData>) results.values);
            }
            notifyDataSetChanged();
        }
    };
}
