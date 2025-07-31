package com.lm.android.tv.player;

import static xyz.doikki.videoplayer.player.BaseVideoView.STATE_PAUSED;
import static xyz.doikki.videoplayer.player.BaseVideoView.STATE_PLAYBACK_COMPLETED;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lm.android.tv.player.model.FileModel;

import java.util.ArrayList;

import xyz.doikki.videocontroller.StandardVideoController;
import xyz.doikki.videoplayer.player.BaseVideoView;

public class PlayActivity extends Activity {
    private static final String TAG = "Player-PlayActivity";

    private int position;
    private ArrayList<FileModel> videos;
    private int playerState;

    private TextView header;

    private SharedPreferences sharedPreferences;
    private int play_mode;
    private int single_play_num;
    private int day_play_num;
    private long currentPositon;

    private int todayPlayCount;
    private int singlePlayCount;

    private xyz.doikki.videoplayer.player.VideoView videoView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        sharedPreferences = getSharedPreferences("player", MODE_PRIVATE);
        // 播放模式,0:列表循环,1:单集循环
        play_mode = sharedPreferences.getInt(SettingActivity.PROPERTY_PLAY_MODE, 0);

        // 单次可播放次数
        single_play_num = sharedPreferences.getInt(SettingActivity.PROPERTY_SINGLE_PLAY, 2);
        if (single_play_num == 10) {
            single_play_num = Integer.MAX_VALUE;
        }

        singlePlayCount = 0;
        // 每日已播放次数
        todayPlayCount = sharedPreferences.getInt(String.valueOf(System.currentTimeMillis() / 1000 / (60 * 60 * 24)), 0);
        // 单日可播放次数
        day_play_num = sharedPreferences.getInt(SettingActivity.PROPERTY_DAY_PLAY, 2);
        if (day_play_num == 10) {
            day_play_num = Integer.MAX_VALUE;
        }
        if (todayPlayCount >= day_play_num) {
            Toast.makeText(this, "今天观看时间已到，明天再看吧", Toast.LENGTH_LONG).show();
            finish();
        }

        String urlPath = getIntent().getStringExtra("parent");
        String json = getIntent().getStringExtra("json");
        videos = new Gson().fromJson(json, new TypeToken<ArrayList<FileModel>>() {
        }.getType());
        position = getIntent().getIntExtra("position", 0);

        header = findViewById(R.id.title);
        header.setText(videos.get(position).name);

        videoView = findViewById(R.id.player);
        StandardVideoController videoController = new StandardVideoController(this);
        videoController.addDefaultControlComponent(videos.get(position).name, false);
        videoView.setVideoController(videoController);
        videoView.setUrl(Urls.serverUrl + urlPath + videos.get(position).url);
        videoView.addOnStateChangeListener(new BaseVideoView.SimpleOnStateChangeListener() {
            @Override
            public void onPlayStateChanged(int playState) {
                super.onPlayStateChanged(playState);
                if (playState == STATE_PLAYBACK_COMPLETED) {
                    // 播放完成
                    singlePlayCount = singlePlayCount + 1;
                    todayPlayCount = todayPlayCount + 1;

                    if (todayPlayCount >= day_play_num) {
                        Toast.makeText(PlayActivity.this, "今天观看时间已到，明天再看吧", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        if (singlePlayCount >= single_play_num) {
                            Toast.makeText(PlayActivity.this, "小朋友，休息一下吧", Toast.LENGTH_LONG).show();
                            finish();
                        } else {
                            if (play_mode == 0) {
                                // 列表循环
                                position = position + 1;
                                while (position < videos.size() && TextUtils.isEmpty(videos.get(position).url)) {
                                    position = position + 1;
                                }
                                if (position < videos.size()) {
                                    header.setText(videos.get(position).name);
                                    videoView.release();
                                    videoView.setUrl(Urls.serverUrl + urlPath + videos.get(position).url);
                                    videoView.start();
                                } else {
                                    finish();
                                }
                            } else {
                                // 单集循环
                                header.setText(videos.get(position).name);
                                videoView.release();
                                videoView.setUrl(Urls.serverUrl + urlPath + videos.get(position).url);
                                videoView.start();
                            }
                        }
                    }
                }
            }
        });
        videoView.start();
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
            if (videoView.isPlaying()) {
                videoView.pause();
            } else if (videoView.getCurrentPlayState() == STATE_PAUSED) {
                videoView.resume();
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            videoView.seekTo(videoView.getCurrentPosition() - 5000);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            videoView.seekTo(videoView.getCurrentPosition() + 5000);
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        videoView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        videoView.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        setValue(String.valueOf(System.currentTimeMillis() / 1000 / (60 * 60 * 24)), todayPlayCount);

        videoView.release();
    }

    @Override
    public void onBackPressed() {
        if (!videoView.onBackPressed()) {
            super.onBackPressed();
        }
    }
}