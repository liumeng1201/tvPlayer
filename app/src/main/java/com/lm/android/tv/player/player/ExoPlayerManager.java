package com.lm.android.tv.player.player;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.Collections;
import java.util.Locale;

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
        play(context, url, null);
    }

    public static void play(Context context, String url, @Nullable String subtitleUrl) {
        ExoPlayer player = getInstance(context);
        MediaItem.Builder media = new MediaItem.Builder().setUri(url);
        if (subtitleUrl != null && !subtitleUrl.trim().isEmpty()) {
            Uri subtitleUri = Uri.parse(subtitleUrl);
            String mimeType = subtitleMimeType(subtitleUri.getLastPathSegment());
            if (mimeType != null) {
                MediaItem.SubtitleConfiguration subtitle = new MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                        .setMimeType(mimeType)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build();
                media.setSubtitleConfigurations(Collections.singletonList(subtitle));
            } else {
                Log.w("ExoPlayerManager", "Unsupported subtitle format: " + subtitleUrl);
            }
        }
        player.setMediaItem(media.build());
        player.prepare();
        player.play();
    }

    @Nullable
    private static String subtitleMimeType(@Nullable String fileName) {
        if (fileName == null) return null;
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".srt")) return MimeTypes.APPLICATION_SUBRIP;
        if (lower.endsWith(".vtt")) return MimeTypes.TEXT_VTT;
        if (lower.endsWith(".ssa") || lower.endsWith(".ass")) return MimeTypes.TEXT_SSA;
        if (lower.endsWith(".ttml") || lower.endsWith(".xml")) return MimeTypes.APPLICATION_TTML;
        return null;
    }

    public static void pause() {
        if (instance != null) {
            instance.pause();
        }
    }

    public static void resume() {
        if (instance != null) {
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
            instance.seekBack();
        }
    }

    public static void seekForward() {
        if (instance != null) {
            instance.seekForward();
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

    public static boolean wantsToPlay() {
        return instance != null && instance.getPlayWhenReady();
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
