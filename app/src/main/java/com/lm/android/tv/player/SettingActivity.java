package com.lm.android.tv.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.FileUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lm.android.tv.player.model.CategoryModel;
import com.lm.android.tv.player.model.UpdateModel;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;

public class SettingActivity extends Activity {
    private static final String TAG = "Player";

    private String[] settings = {"单次播放", "每日播放", "播放方式", "检查更新"};
    public static final String PROPERTY_SINGLE_PLAY = "single_play";
    public static final String PROPERTY_DAY_PLAY = "day_play";
    public static final String PROPERTY_PLAY_MODE = "play_mode";

    private SharedPreferences sharedPreferences;
    private BaseRecyclerAdapter adapter;

    private ProgressDialog loadingDialog;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);

            if (msg.what == 1000) {
                String json = msg.getData().getString("json");
                final UpdateModel update = new Gson().fromJson(json, new TypeToken<UpdateModel>() {
                }.getType());

                if (update.versionCode > BuildConfig.VERSION_CODE) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(SettingActivity.this);
                    builder.setTitle("检查更新")
                            .setMessage("当前版本：" + BuildConfig.VERSION_NAME + "\n检测到新版本：" + update.versionName + "\n是否升级？")
                            .setPositiveButton("升级", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    loadingDialog = new ProgressDialog(SettingActivity.this);
                                    loadingDialog.setMessage("下载中，请稍后...");
                                    loadingDialog.show();
                                    String filePath = getExternalFilesDir(null).getAbsolutePath() + "/apk/app-release.apk";
                                    DownloadRunnable downloadRunnable = new DownloadRunnable(update.downloadUrl, filePath);
                                    new Thread(downloadRunnable).start();
                                }
                            });
                    builder.create().show();
                } else {
                    Toast.makeText(SettingActivity.this, "已是最新版本", Toast.LENGTH_LONG).show();
                }
            } else if (msg.what == 1001) {
                if (loadingDialog != null && loadingDialog.isShowing()) {
                    loadingDialog.dismiss();
                }
                String filePath = msg.getData().getString("filePath");
                AppUtils.installApp(filePath);
            }
        }
    };

    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            getData("https://enable-ireading.oss-cn-shanghai.aliyuncs.com/cartoon/update.json");
        }
    };

    class DownloadRunnable implements Runnable {
        String url;
        String filePath;

        public DownloadRunnable(String url, String filePath) {
            this.url = url;
            this.filePath = filePath;
        }

        @Override
        public void run() {
            download(url, filePath);
        }
    }

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
                    content = content + "  " + (getValue(PROPERTY_SINGLE_PLAY) == 10 ? "不限制" : (getValue(PROPERTY_SINGLE_PLAY) + " 集"));
                } else if (position == 1) {
                    content = content + "  " + (getValue(PROPERTY_DAY_PLAY) == 10 ? "不限制" : (getValue(PROPERTY_DAY_PLAY) + " 集"));
                } else if (position == 2) {
                    content = content + "  " + (getValue(PROPERTY_PLAY_MODE) == 0 ? "列表循环" : "单集循环");
                } else if (position == 3) {
                    content = content + "    " + "当前版本：" + BuildConfig.VERSION_NAME;
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
                } else if (position == 2) {
                    showPlayModeSelectDialog();
                } else if (position == 3) {
                    new Thread(runnable).start();
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
        if (key.equalsIgnoreCase(PROPERTY_PLAY_MODE)) {
            return sharedPreferences.getInt(key, 0);
        }
        return sharedPreferences.getInt(key, 2);
    }

    private void showNumSelectDialog(final int type) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请选择").setItems(new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "不限制"}, new DialogInterface.OnClickListener() {
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

    private void showPlayModeSelectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请选择").setItems(new String[]{"列表循环", "单集循环"}, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                setValue(PROPERTY_PLAY_MODE, i);
                adapter.notifyDataSetChanged();
            }
        });
        builder.create().show();
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
            msg.what = 1000;
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

    private void download(String urlString, String filePath) {
        HttpsURLConnection urlConnection = null;
        try {
            java.net.URL url = new java.net.URL(urlString);
            urlConnection = (HttpsURLConnection) url.openConnection();

            FileUtils.createFileByDeleteOldFile(filePath);
            File file = new File(filePath);
            FileOutputStream outputStream = new FileOutputStream(file);

            InputStream inputStream = urlConnection.getInputStream();
            BufferedInputStream bfi = new BufferedInputStream(inputStream);
            byte[] bytes = new byte[1024];
            int len;
            while ((len = bfi.read(bytes)) != -1) {
                outputStream.write(bytes, 0, len);
            }

            outputStream.close();
            inputStream.close();
            bfi.close();

            Message msg = new Message();
            Bundle data = new Bundle();
            data.putString("filePath", filePath);
            msg.setData(data);
            msg.what = 1001;
            handler.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            urlConnection.disconnect();
        }
    }
}