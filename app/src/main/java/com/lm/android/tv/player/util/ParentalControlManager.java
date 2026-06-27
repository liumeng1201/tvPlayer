package com.lm.android.tv.player.util;

import android.content.Context;

/**
 * 播放时长限制管理器（替代原有的集数控制）
 *
 * 逻辑：
 * 1. 单次观看最多累计 15 分钟（仅实际播放状态计时）
 * 2. 达到上限后等待当前视频播完，启动 2 小时冷却
 * 3. 冷却期内无法播放，提示剩余等待时间
 */
public final class ParentalControlManager {

    private ParentalControlManager() {}

    // ===================== 累计时长 =====================

    /** 读取本次已累计的播放时长（毫秒） */
    public static long getAccumulatedMs(Context context) {
        return SharedPrefsHelper.getLong(context, SharedPrefsHelper.KEY_ACCUMULATED_MS, 0);
    }

    /** 追加播放时长（毫秒） */
    public static void addAccumulatedTime(Context context, long ms) {
        long current = getAccumulatedMs(context);
        SharedPrefsHelper.putLong(context, SharedPrefsHelper.KEY_ACCUMULATED_MS, current + ms);
    }

    // ===================== 上限判断 =====================

    /** 本次累计时长是否已达到 15 分钟上限 */
    public static boolean isLimitReached(Context context) {
        return getAccumulatedMs(context) >= SharedPrefsHelper.SESSION_MAX_MS;
    }

    // ===================== 冷却期 =====================

    /** 是否处于冷却期内 */
    public static boolean isInCooldown(Context context) {
        long cooldownUntil = SharedPrefsHelper.getLong(context, SharedPrefsHelper.KEY_COOLDOWN_UNTIL, 0);
        if (cooldownUntil == 0) {
            return false;
        }
        if (System.currentTimeMillis() >= cooldownUntil) {
            // 冷却已结束 → 清除状态
            clearCooldown(context);
            return false;
        }
        return true;
    }

    /** 启动 2 小时冷却（同时重置累计时长） */
    public static void startCooldown(Context context) {
        long cooldownUntil = System.currentTimeMillis() + SharedPrefsHelper.COOLDOWN_MS;
        SharedPrefsHelper.putLong(context, SharedPrefsHelper.KEY_COOLDOWN_UNTIL, cooldownUntil);
        SharedPrefsHelper.putLong(context, SharedPrefsHelper.KEY_ACCUMULATED_MS, 0);
    }

    /** 清除冷却状态 */
    private static void clearCooldown(Context context) {
        SharedPrefsHelper.putLong(context, SharedPrefsHelper.KEY_COOLDOWN_UNTIL, 0);
        SharedPrefsHelper.putLong(context, SharedPrefsHelper.KEY_ACCUMULATED_MS, 0);
    }

    /** 获取冷却剩余时间的格式化文本 */
    public static String getCooldownRemainingText(Context context) {
        long cooldownUntil = SharedPrefsHelper.getLong(context, SharedPrefsHelper.KEY_COOLDOWN_UNTIL, 0);
        long remaining = cooldownUntil - System.currentTimeMillis();
        if (remaining <= 0) return "";

        long hours = remaining / 3_600_000;
        long minutes = (remaining % 3_600_000) / 60_000;

        if (hours > 0) {
            return hours + "小时" + minutes + "分钟";
        } else {
            return minutes + "分钟";
        }
    }

    // ===================== 播放方式（保留） =====================

    /**
     * 获取播放模式：0=列表循环，1=单集循环
     */
    public static int getPlayMode(Context context) {
        return SharedPrefsHelper.getInt(context, SharedPrefsHelper.KEY_PLAY_MODE, 0);
    }
}
