package com.lm.android.tv.player;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lm.android.tv.player.model.VideoModel;

import java.util.ArrayList;

public class ImageActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);

        String json = getIntent().getStringExtra("json");
        ArrayList<VideoModel> videos = new Gson().fromJson(json, new TypeToken<ArrayList<VideoModel>>() {
        }.getType());
        int position = getIntent().getIntExtra("position", 0);

        ImageView imageView = findViewById(R.id.image);
        Glide.with(this).load(videos.get(position).cover).into(imageView);

        TextView header = findViewById(R.id.title);
        header.setText(videos.get(position).name);
    }
}