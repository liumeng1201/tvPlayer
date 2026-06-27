package com.lm.android.tv.player;

import android.os.Bundle;
import android.view.KeyEvent;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.lm.android.tv.player.ui.main.MainFragment;
import com.lm.android.tv.player.ui.player.PlayerFragment;

/**
 * 单 Activity 容器
 */
public class MainActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new MainFragment())
                    .commit();
        }
    }

    /**
     * 转发遥控器/键盘按键事件
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // ESC → 返回
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            onBackPressed();
            return true;
        }

        // 空格 → 播放/暂停（模拟遥控器 OK 键）
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            Fragment current = getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_container);
            if (current instanceof PlayerFragment) {
                return ((PlayerFragment) current).onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, event);
            }
            return true;
        }

        Fragment current = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container);

        if (current instanceof PlayerFragment) {
            if (((PlayerFragment) current).onKeyDown(keyCode, event)) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 处理返回键：
     * - PlayerFragment/ImageFragment/SettingFragment → pop back stack
     * - MainFragment → navigate up (子目录回到上级) or finish (根目录退出)
     */
    @Override
    public void onBackPressed() {
        Fragment current = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container);

        if (current instanceof MainFragment) {
            if (!((MainFragment) current).onBackPressed()) {
                // 已是根目录，退出应用
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }
}
