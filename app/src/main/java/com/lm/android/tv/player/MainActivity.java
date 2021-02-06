package com.lm.android.tv.player;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lm.android.tv.player.model.CategoryModel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity {
    private static final String TAG = "Player";
    private RecyclerView recyclerView;
    private BaseRecyclerAdapter adapter;

    private ArrayList<CategoryModel> categorys = new ArrayList<>();
    private Gson gson;
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            String json = msg.getData().getString("json");
            categorys = gson.fromJson(json, new TypeToken<ArrayList<CategoryModel>>() {
            }.getType());
            adapter.notifyDataSetChanged();
        }
    };

    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            getData("https://enable-ireading.oss-cn-shanghai.aliyuncs.com/cartoon/PeppaPig/cartoon.json");
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gson = new Gson();

        findViewById(R.id.setting).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, SettingActivity.class));
            }
        });

        adapter = new BaseRecyclerAdapter() {
            @Override
            protected void onBindView(@NonNull BaseViewHolder holder, @NonNull int position) {
                ImageView cover = holder.itemView.findViewById(R.id.cover);
                TextView title = holder.itemView.findViewById(R.id.title);

                Glide.with(MainActivity.this).load(categorys.get(position).cover).into(cover);
                title.setText(categorys.get(position).name);
            }

            @Override
            protected int getLayoutResId(int position) {
                return R.layout.item_category;
            }

            @Override
            public int getItemCount() {
                return categorys.size();
            }
        };
        adapter.setOnItemClickListener(new CustomOnItemClickListener() {
            @Override
            public void onItemClick(@NonNull int position) {
                Intent intent = new Intent(MainActivity.this, CategoryActivity.class);
                intent.putExtra("json", gson.toJson(categorys.get(position)));
                startActivity(intent);
            }
        });

        recyclerView = findViewById(R.id.category);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 4));
        recyclerView.setAdapter(adapter);

        new Thread(runnable).start();
    }

    private void getData(String urlString) {
        BufferedReader reader = null;
        HttpsURLConnection urlConnection = null;
        try {
            java.net.URL url = new java.net.URL(urlString);
            urlConnection = (HttpsURLConnection) url.openConnection();
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