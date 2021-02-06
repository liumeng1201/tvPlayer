package com.lm.android.tv.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SettingActivity extends Activity {
    private static final String TAG = "Player";

    private String[] settings = {"单次播放", "每日播放", "更新"};
    public static final String PROPERTY_SINGLE_PLAY = "single_play";
    public static final String PROPERTY_DAY_PLAY = "day_play";

    private SharedPreferences sharedPreferences;
    private BaseRecyclerAdapter adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        sharedPreferences = getSharedPreferences("player", MODE_PRIVATE);

        adapter = new BaseRecyclerAdapter() {
            @Override
            protected void onBindView(@NonNull BaseViewHolder holder, @NonNull int position) {
                TextView textView = holder.itemView.findViewById(R.id.text);
                String content = settings[position];
                if (position == 0) {
                    content = content + " " + getValue(PROPERTY_SINGLE_PLAY) + " 集";
                } else if (position == 1) {
                    content = content + " " + getValue(PROPERTY_DAY_PLAY) + " 集";
                }
                textView.setText(content);
            }

            @Override
            protected int getLayoutResId(int position) {
                return R.layout.item_setting;
            }

            @Override
            public int getItemCount() {
                return settings.length;
            }
        };
        adapter.setOnItemClickListener(new CustomOnItemClickListener() {
            @Override
            public void onItemClick(@NonNull int position) {
                if (position == 0 || position == 1) {
                    showNumSelectDialog(position);
                }
            }
        });

        RecyclerView recyclerView = findViewById(R.id.setting);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(adapter);
    }

    private void setValue(String key, int value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    private int getValue(String key) {
        return sharedPreferences.getInt(key, 2);
    }

    private void showNumSelectDialog(final int type) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请选择").setItems(new String[]{"1", "2", "3", "4", "5", "6"}, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (type == 0) {
                    setValue(PROPERTY_SINGLE_PLAY, i + 1);
                } else if (type == 1) {
                    setValue(PROPERTY_DAY_PLAY, i + 1);
                }
                adapter.notifyDataSetChanged();
            }
        });
        builder.create().show();
    }

}