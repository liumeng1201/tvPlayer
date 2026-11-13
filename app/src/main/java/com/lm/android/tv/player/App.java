package com.lm.android.tv.player;

import android.app.Application;
import com.lm.android.tv.player.util.ParentalControlManager;

/**
 * Application 入口
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ParentalControlManager.recoverInterruptedPlayback(this);
    }
}
