package com.lm.android.tv.player.player;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * ExoPlayer 管理器 —— 单例持有播放器实例
 */
public final class ExoPlayerManager {

    private static volatile ExoPlayer instance;

    private ExoPlayerManager() {}

    /**
     * 获取全局 ExoPlayer 实例
     */
    @OptIn(markerClass = UnstableApi.class)
    public static ExoPlayer getInstance(Context context) {
        if (instance == null) {
            synchronized (ExoPlayerManager.class) {
                if (instance == null) {
                    instance = new ExoPlayer.Builder(context)
                            .setSeekBackIncrementMs(5000)
                            .setSeekForwardIncrementMs(5000)
                            .build();
                }
            }
        }
        return instance;
    }

    public static void play(Context context, String url) {
        ExoPlayer player = getInstance(context);
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.play();
    }

    public static void pause() {
        if (instance != null) {
            instance.pause();
        }
    }

    public static void resume() {
        if (instance != null && instance.getPlaybackState() == Player.STATE_READY) {
            instance.play();
        }
    }

    public static void seekTo(long positionMs) {
        if (instance != null) {
            instance.seekTo(positionMs);
        }
    }

    public static void seekBack() {
        if (instance != null) {
            long pos = Math.max(0, instance.getCurrentPosition() - 5000);
            instance.seekTo(pos);
        }
    }

    public static void seekForward() {
        if (instance != null) {
            long pos = Math.min(instance.getContentDuration(),
                    instance.getCurrentPosition() + 5000);
            instance.seekTo(pos);
        }
    }

    public static void release() {
        if (instance != null) {
            instance.stop();
            instance.release();
            instance = null;
        }
    }

    public static void stop() {
        if (instance != null) {
            instance.stop();
        }
    }

    public static boolean isPlaying() {
        return instance != null && instance.isPlaying();
    }

    public static long getCurrentPosition() {
        return instance != null ? instance.getCurrentPosition() : 0;
    }

    public static long getDuration() {
        return instance != null ? instance.getDuration() : 0;
    }

    public static void addListener(Player.Listener listener) {
        if (instance != null) {
            instance.addListener(listener);
        }
    }

    public static void removeListener(Player.Listener listener) {
        if (instance != null) {
            instance.removeListener(listener);
        }
    }
}
