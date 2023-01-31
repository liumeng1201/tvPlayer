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
import com.lm.android.tv.player.model.SubCategoryModel;
import com.lm.android.tv.player.model.VideoModel;

import java.util.ArrayList;

public class CategoryActivity extends Activity {
    private static final String TAG = "Player";

    private SubCategoryModel subCategoryModel;
    private CategoryModel category;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);
        String type = getIntent().getStringExtra("type");
        String json = getIntent().getStringExtra("json");
        if (type.equalsIgnoreCase("category")) {
            category = new Gson().fromJson(json, new TypeToken<CategoryModel>() {
            }.getType());
            subCategoryModel = null;
        } else {
            subCategoryModel = new Gson().fromJson(json, new TypeToken<SubCategoryModel>() {
            }.getType());
            category = null;
        }

        TextView header = findViewById(R.id.title);
        if (category != null) {
            header.setText(category.name);
        }
        if (subCategoryModel != null) {
            header.setText(subCategoryModel.name);
        }

        BaseRecyclerAdapter adapter = new BaseRecyclerAdapter() {
            @Override
            protected void onBindView(@NonNull BaseViewHolder holder, @NonNull int position) {
                ImageView cover = holder.itemView.findViewById(R.id.cover);
                TextView title = holder.itemView.findViewById(R.id.title);

                if (category != null) {
                    Glide.with(CategoryActivity.this).load(category.videos.get(position).cover).into(cover);
                    title.setText(category.videos.get(position).name);
                }
                if (subCategoryModel != null) {
                    Glide.with(CategoryActivity.this).load(subCategoryModel.videos.get(position).cover).into(cover);
                    title.setText(subCategoryModel.videos.get(position).name);
                }
            }

            @Override
            protected int getLayoutResId(int position) {
                return R.layout.item_category;
            }

            @Override
            public int getItemCount() {
                if (category != null) {
                    return category.videos.size();
                }
                if (subCategoryModel != null) {
                    return subCategoryModel.videos.size();
                }
                return 0;
            }
        };
        adapter.setOnItemClickListener(new CustomOnItemClickListener() {
            @Override
            public void onItemClick(@NonNull int position) {
                if (category != null) {
                    Intent intent = new Intent(CategoryActivity.this, CategoryActivity.class);
                    intent.putExtra("json", new Gson().toJson(category.videos.get(position)));
                    intent.putExtra("position", position);
                    intent.putExtra("type", "sub-category");
                    startActivity(intent);
                }
                if (subCategoryModel != null) {
                    Intent intent = new Intent(CategoryActivity.this, PlayActivity.class);
                    intent.putExtra("json", new Gson().toJson(subCategoryModel.videos));
                    intent.putExtra("position", position);
                    startActivity(intent);
                }
            }
        });

        RecyclerView recyclerView = findViewById(R.id.category);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 4));
        recyclerView.setAdapter(adapter);
    }

}