package com.lm.android.tv.player;

import static xyz.doikki.videoplayer.player.BaseVideoView.STATE_PAUSED;
import static xyz.doikki.videoplayer.player.BaseVideoView.STATE_PLAYBACK_COMPLETED;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
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

    private TextView header;

    private SharedPreferences sharedPreferences;
    private int play_mode;
    private int single_play_num;
    private int day_play_num;

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

        // 单次已播放次数
        singlePlayCount = sharedPreferences.getInt(SettingActivity.PROPERTY_SINGLE_PLAY_COUNT, 0);
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

        Log.d("lm-lm-lm", "singlePlayCount=" + singlePlayCount + "\nsingle_play_num=" + single_play_num);
        if (singlePlayCount >= single_play_num) {
            // 已达到单次播放次数最大值
            long lastFinishTime = sharedPreferences.getLong(SettingActivity.PROPERTY_SINGLE_PLAY_LAST_FINISH_TIME, System.currentTimeMillis());
            Log.d("lm-lm-lm", "lastFinishTime=" + lastFinishTime);
            if (System.currentTimeMillis() - lastFinishTime < 1000 * 60 * 30) {
                // 已达单次播放最大值之后再次播放时间间隔小于30分钟，则不允许播放
                Toast.makeText(PlayActivity.this, "小朋友，休息一下吧", Toast.LENGTH_LONG).show();
                finish();
            } else {
                // 间隔时间大于30分钟则允许再次播放
                singlePlayCount = 0;
            }
        }

        String urlPath = getIntent().getStringExtra("parent");
        String json = getIntent().getStringExtra("json");
        videos = new Gson().fromJson(json, new TypeToken<ArrayList<FileModel>>() {
        }.getType());
        position = getIntent().getIntExtra("position", 0);

        header = findViewById(R.id.title);
        setVideoTitle(videos.get(position).name);

        videoView = findViewById(R.id.player);
        StandardVideoController videoController = new StandardVideoController(this);
        videoController.addDefaultControlComponent(videos.get(position).name, false);
        videoView.setVideoController(videoController);
        videoView.setUrl(Urls.encodeChineseUrl(Urls.serverUrl + urlPath + videos.get(position).url));
        videoView.addOnStateChangeListener(new BaseVideoView.SimpleOnStateChangeListener() {
            @Override
            public void onPlayStateChanged(int playState) {
                super.onPlayStateChanged(playState);
                if (playState == STATE_PLAYBACK_COMPLETED) {
                    // 播放完成
                    singlePlayCount = singlePlayCount + 1;
                    todayPlayCount = todayPlayCount + 1;
                    sharedPreferences.edit().putInt(String.valueOf(System.currentTimeMillis() / 1000 / (60 * 60 * 24)), todayPlayCount).apply();
                    sharedPreferences.edit().putLong(SettingActivity.PROPERTY_SINGLE_PLAY_LAST_FINISH_TIME, System.currentTimeMillis()).apply();
                    sharedPreferences.edit().putInt(SettingActivity.PROPERTY_SINGLE_PLAY_COUNT, singlePlayCount).apply();

                    if (todayPlayCount >= day_play_num) {
                        Toast.makeText(PlayActivity.this, "今天观看时间已到，明天再看吧", Toast.LENGTH_LONG).show();
                        videoView.release();
                        finish();
                    } else {
                        if (singlePlayCount >= single_play_num) {
                            Toast.makeText(PlayActivity.this, "小朋友，休息一下吧", Toast.LENGTH_LONG).show();
                            videoView.release();
                            finish();
                        } else {
                            if (play_mode == 0) {
                                // 列表循环
                                position = position + 1;
                                while (position < videos.size() && TextUtils.isEmpty(videos.get(position).url)) {
                                    position = position + 1;
                                }
                                if (position >= videos.size()) {
                                    position = 0;
                                }
                                setVideoTitle(videos.get(position).name);
                                videoView.release();
                                videoView.setUrl(Urls.encodeChineseUrl(Urls.serverUrl + urlPath + videos.get(position).url));
                                videoView.start();
                            } else {
                                // 单集循环
                                setVideoTitle(videos.get(position).name);
                                // videoView.release();
                                // videoView.setUrl(Urls.encodeChineseUrl(Urls.serverUrl + urlPath + videos.get(position).url));
                                videoView.replay(true);
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

    private void setVideoTitle(String title) {
        header.setVisibility(TextView.VISIBLE);
        header.setText(title);
        header.postDelayed(new Runnable() {
            @Override
            public void run() {
                header.setVisibility(TextView.GONE);
            }
        }, 5000);
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