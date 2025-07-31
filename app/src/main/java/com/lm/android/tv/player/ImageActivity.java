package com.lm.android.tv.player;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lm.android.tv.player.model.FileModel;

import java.util.ArrayList;

public class ImageActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);

        String urlPath = getIntent().getStringExtra("parent");
        String json = getIntent().getStringExtra("json");
        ArrayList<FileModel> videos = new Gson().fromJson(json, new TypeToken<ArrayList<FileModel>>() {
        }.getType());
        int position = getIntent().getIntExtra("position", 0);

        ImageView imageView = findViewById(R.id.image);
        Glide.with(this).load(Urls.serverUrl + urlPath + videos.get(position).url).into(imageView);

        TextView header = findViewById(R.id.title);
        header.setText(videos.get(position).name);
    }
}