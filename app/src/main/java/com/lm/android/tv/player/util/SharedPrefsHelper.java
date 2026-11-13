package com.lm.android.tv.player.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences 封装
 */
public final class SharedPrefsHelper {

    private static final String PREFS_NAME = "player";

    public static final String KEY_PLAY_MODE = "play_mode";
    public static final String KEY_UPDATE_DOWNLOAD_ID = "update_download_id";
    public static final String KEY_LAST_POSITION_PREFIX = "last_pos_";
    public static final String KEY_RESTART_HINT_COUNT = "restart_hint_count";

    // ========== 时长限制相关 ==========
    public static final String KEY_ACCUMULATED_MS = "accumulated_ms";
    public static final String KEY_COOLDOWN_UNTIL = "cooldown_until";
    public static final String KEY_PLAYBACK_PAUSED_AT = "playback_paused_at";

    /** 单次观看上限（20 分钟） */
    public static final long SESSION_MAX_MS = 20 * 60 * 1000L;
    /** 冷却时长（2 小时） */
    public static final long COOLDOWN_MS = 2 * 60 * 60 * 1000L;
    /** 累计时长更新间隔（5 秒） */
    public static final long TRACKING_INTERVAL_MS = 5000L;

    public static final int KEY_RESTART_HINT_MAX = 10;

    private SharedPrefsHelper() {}

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static int getInt(Context context, String key, int defaultValue) {
        return getPrefs(context).getInt(key, defaultValue);
    }

    public static void putInt(Context context, String key, int value) {
        getPrefs(context).edit().putInt(key, value).apply();
    }

    public static long getLong(Context context, String key, long defaultValue) {
        return getPrefs(context).getLong(key, defaultValue);
    }

    public static void putLong(Context context, String key, long value) {
        getPrefs(context).edit().putLong(key, value).apply();
    }

    public static int getLastPosition(Context context, String path) {
        return getInt(context, KEY_LAST_POSITION_PREFIX + path, 0);
    }

    public static void putLastPosition(Context context, String path, int position) {
        putInt(context, KEY_LAST_POSITION_PREFIX + path, position);
    }
}
