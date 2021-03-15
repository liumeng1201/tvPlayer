package com.lm.android.tv.player;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.aliyun.player.AliPlayer;
import com.aliyun.player.AliPlayerFactory;
import com.aliyun.player.IPlayer;
import com.aliyun.player.bean.InfoBean;
import com.aliyun.player.bean.InfoCode;
import com.aliyun.player.source.UrlSource;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lm.android.tv.player.model.VideoModel;

import java.util.ArrayList;

public class PlayActivity extends Activity {
    private static final String TAG = "Player-PlayActivity";

    private int position;
    private ArrayList<VideoModel> videos;
    private AliPlayer aliyunVodPlayer;
    private int playerState;

    private TextView header;

    private SharedPreferences sharedPreferences;
    private int single_play;
    private int day_play;
    private long currentPositon;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        sharedPreferences = getSharedPreferences("player", MODE_PRIVATE);
        // 单次可播放次数
        single_play = sharedPreferences.getInt(SettingActivity.PROPERTY_SINGLE_PLAY, 2);
        if (single_play == 10) {
            single_play = Integer.MAX_VALUE;
        }

        long todayTime = System.currentTimeMillis() / 1000 / (60 * 60 * 24);
        // 每日已播放次数
        int todayPlayCount = sharedPreferences.getInt(String.valueOf(todayTime), 0);
        // 单日可播放次数
        day_play = sharedPreferences.getInt(SettingActivity.PROPERTY_DAY_PLAY, 2);
        if (day_play == 10) {
            day_play = Integer.MAX_VALUE;
        }
        if (todayPlayCount >= day_play) {
            Toast.makeText(this, "今天观看时间已到，明天再看吧", Toast.LENGTH_LONG).show();
            finish();
        }

        String json = getIntent().getStringExtra("json");
        videos = new Gson().fromJson(json, new TypeToken<ArrayList<VideoModel>>() {
        }.getType());
        position = getIntent().getIntExtra("position", 0);

        header = findViewById(R.id.title);
        header.setText(videos.get(position).name);

        aliyunVodPlayer = AliPlayerFactory.createAliPlayer(getApplicationContext());
        // 关闭循环播放
        aliyunVodPlayer.setLoop(false);
        aliyunVodPlayer.setOnStateChangedListener(new IPlayer.OnStateChangedListener() {
            @Override
            public void onStateChanged(int state) {
                playerState = state;
            }
        });
        aliyunVodPlayer.setOnInfoListener(new IPlayer.OnInfoListener() {
            @Override
            public void onInfo(InfoBean infoBean) {
                if (infoBean.getCode() == InfoCode.CurrentPosition) {
                    currentPositon = infoBean.getExtraValue();
                }
            }
        });
        aliyunVodPlayer.setOnCompletionListener(new IPlayer.OnCompletionListener() {
            @Override
            public void onCompletion() {
                position = position + 1;
                int start = getIntent().getIntExtra("position", 0) + single_play;
                if (position >= start) {
                    Toast.makeText(PlayActivity.this, "小朋友，休息一下吧", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    if (position < videos.size()) {
                        UrlSource urlSource = new UrlSource();
                        header.setText(videos.get(position).name);
                        urlSource.setUri(videos.get(position).video);
                        aliyunVodPlayer.setDataSource(urlSource);
                        aliyunVodPlayer.prepare();
                        aliyunVodPlayer.start();
                    } else {
                        finish();
                    }
                }
            }
        });

        UrlSource urlSource = new UrlSource();
        urlSource.setUri(videos.get(position).video);
        aliyunVodPlayer.setDataSource(urlSource);
        aliyunVodPlayer.setAutoPlay(true);
        aliyunVodPlayer.prepare();

        SurfaceView surfaceView = findViewById(R.id.play_view);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
                aliyunVodPlayer.setDisplay(surfaceHolder);
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                aliyunVodPlayer.redraw();
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
                aliyunVodPlayer.setDisplay(null);
            }
        });

        setValue(String.valueOf(todayTime), todayPlayCount + 1);
    }

    private void setValue(String key, int value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) {
            if (playerState == IPlayer.started) {
                aliyunVodPlayer.pause();
            } else if (playerState == IPlayer.paused) {
                aliyunVodPlayer.start();
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            aliyunVodPlayer.seekTo(currentPositon - 5000);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            aliyunVodPlayer.seekTo(currentPositon + 5000);
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playerState == IPlayer.paused) {
            aliyunVodPlayer.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        aliyunVodPlayer.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        aliyunVodPlayer.stop();
        aliyunVodPlayer.release();
    }
}