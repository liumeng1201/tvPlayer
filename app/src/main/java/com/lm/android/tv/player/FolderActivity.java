package com.lm.android.tv.player;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lm.android.tv.player.model.FileModel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;

public class FolderActivity extends Activity {
    private static final String TAG = "Player";
    private RecyclerView recyclerView;
    private BaseRecyclerAdapter adapter;

    private String urlPath;
    private ArrayList<FileModel> files = new ArrayList<>();
    private Gson gson;
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            String json = msg.getData().getString("json");
            files = gson.fromJson(json, new TypeToken<ArrayList<FileModel>>() {
            }.getType());
            adapter.notifyDataSetChanged();
        }
    };

    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            getData(Urls.serverUrl + urlPath + "files.json");
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);

        gson = new Gson();

        String folder = getIntent().getStringExtra("folder");
        String parent = getIntent().getStringExtra("parent");
        urlPath = parent + folder + "/";

        TextView header = findViewById(R.id.title);
        header.setText(folder);

        adapter = new BaseRecyclerAdapter() {
            @Override
            protected void onBindView(@NonNull BaseViewHolder holder, @NonNull int position) {
                ImageView cover = holder.itemView.findViewById(R.id.cover);
                TextView title = holder.itemView.findViewById(R.id.title);

                Glide.with(FolderActivity.this).load(Urls.serverUrl + urlPath + files.get(position).cover).into(cover);
                title.setText(files.get(position).name);
            }

            @Override
            protected int getLayoutResId(int position) {
                return R.layout.item_category;
            }

            @Override
            public int getItemCount() {
                return files.size();
            }
        };
        adapter.setOnItemClickListener(new CustomOnItemClickListener() {
            @Override
            public void onItemClick(@NonNull int position) {
                if (files.get(position).type == 0) {
                    Intent intent = new Intent(FolderActivity.this, FolderActivity.class);
                    intent.putExtra("folder", files.get(position).name);
                    intent.putExtra("parent", urlPath);
                    startActivity(intent);
                } else if (files.get(position).type == 1) {
                    Intent intent = new Intent(FolderActivity.this, PlayActivity.class);
                    intent.putExtra("json", new Gson().toJson(files));
                    intent.putExtra("position", position);
                    intent.putExtra("parent", urlPath);
                    startActivity(intent);
                } else if (files.get(position).type == 2) {
                    Intent intent = new Intent(FolderActivity.this, ImageActivity.class);
                    intent.putExtra("json", new Gson().toJson(files));
                    intent.putExtra("position", position);
                    intent.putExtra("parent", urlPath);
                    startActivity(intent);
                }
            }
        });

        recyclerView = findViewById(R.id.category);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 4));
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        new Thread(runnable).start();
    }

    private void getData(String urlString) {
        BufferedReader reader = null;
        HttpURLConnection urlConnection = null;
        try {
            java.net.URL url = new java.net.URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "utf-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String json = sb.toString();
            Message msg = new Message();
            Bundle data = new Bundle();
            data.putString("json", json);
            msg.setData(data);
            handler.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            urlConnection.disconnect();
            if (null != reader) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "JSON feed closed", e);
                }
            }
        }
    }
}