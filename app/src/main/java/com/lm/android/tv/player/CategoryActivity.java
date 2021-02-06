package com.lm.android.tv.player;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lm.android.tv.player.model.CategoryModel;
import com.lm.android.tv.player.model.VideoModel;

import java.util.ArrayList;

public class CategoryActivity extends Activity {
    private static final String TAG = "Player";

    private CategoryModel category;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);
        String json = getIntent().getStringExtra("json");
        category = new Gson().fromJson(json, new TypeToken<CategoryModel>() {
        }.getType());

        TextView header = findViewById(R.id.title);
        header.setText(category.name);

        BaseRecyclerAdapter adapter = new BaseRecyclerAdapter() {
            @Override
            protected void onBindView(@NonNull BaseViewHolder holder, @NonNull int position) {
                ImageView cover = holder.itemView.findViewById(R.id.cover);
                TextView title = holder.itemView.findViewById(R.id.title);

                Glide.with(CategoryActivity.this).load(category.videos.get(position).cover).into(cover);
                title.setText(category.videos.get(position).name);
            }

            @Override
            protected int getLayoutResId(int position) {
                return R.layout.item_category;
            }

            @Override
            public int getItemCount() {
                return category.videos.size();
            }
        };
        adapter.setOnItemClickListener(new CustomOnItemClickListener() {
            @Override
            public void onItemClick(@NonNull int position) {
                Intent intent = new Intent(CategoryActivity.this, PlayActivity.class);
                intent.putExtra("json", new Gson().toJson(category.videos));
                intent.putExtra("position", position);
                startActivity(intent);
            }
        });

        RecyclerView recyclerView = findViewById(R.id.category);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 4));
        recyclerView.setAdapter(adapter);
    }

}