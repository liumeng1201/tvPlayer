package com.lm.android.tv.player.player;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
                            .setAudioAttributes(AudioAttributes.DEFAULT, true)
                            .setSeekBackIncrementMs(5000)
                            .setSeekForwardIncrementMs(5000)
                            .build();
                }
            }
        }
        return instance;
    }

    public static void play(Context context, String url) {
        play(context, url, (List<String>) null);
    }

    public static void play(Context context, String url, @Nullable String subtitleUrl) {
        List<String> list = (subtitleUrl == null || subtitleUrl.trim().isEmpty())
                ? null : Collections.singletonList(subtitleUrl);
        play(context, url, list, 0, 0, true);
    }

    public static void play(Context context, String url, @Nullable List<String> subtitleUrls) {
        play(context, url, subtitleUrls, 0, 0, true);
    }

    public static void play(Context context, String url, @Nullable String subtitleUrl,
                            long positionMs, boolean playWhenReady) {
        List<String> list = (subtitleUrl == null || subtitleUrl.trim().isEmpty())
                ? null : Collections.singletonList(subtitleUrl);
        play(context, url, list, 0, positionMs, playWhenReady);
    }

    public static void play(Context context, String url, @Nullable List<String> subtitleUrls,
                            int defaultSubtitleIndex, long positionMs, boolean playWhenReady) {
        ExoPlayer player = getInstance(context);
        MediaItem.Builder media = new MediaItem.Builder().setUri(url);
        if (subtitleUrls != null && !subtitleUrls.isEmpty()) {
            List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
            for (int i = 0; i < subtitleUrls.size(); i++) {
                String subUrl = subtitleUrls.get(i);
                if (subUrl == null || subUrl.trim().isEmpty()) continue;
                Uri subtitleUri = Uri.parse(subUrl);
                String mimeType = subtitleMimeType(subtitleUri.getLastPathSegment());
                if (mimeType != null) {
                    MediaItem.SubtitleConfiguration subtitle = new MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                            .setMimeType(mimeType)
                            .setId(String.valueOf(i))
                            .setSelectionFlags(i == defaultSubtitleIndex ? C.SELECTION_FLAG_DEFAULT : 0)
                            .build();
                    configs.add(subtitle);
                } else {
                    Log.w("ExoPlayerManager", "Unsupported subtitle format: " + subUrl);
                }
            }
            if (!configs.isEmpty()) {
                media.setSubtitleConfigurations(configs);
            }
        }
        player.setPlayWhenReady(playWhenReady);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters()
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build());
        player.setMediaItem(media.build(), positionMs);
        player.prepare();
    }

    @OptIn(markerClass = UnstableApi.class)
    public static void selectSubtitle(int targetIndex) {
        ExoPlayer player = instance;
        if (player == null) return;
        Tracks tracks = player.getCurrentTracks();
        int textTrackCounter = 0;
        TrackGroup targetGroup = null;
        int targetTrackIndex = -1;

        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() == C.TRACK_TYPE_TEXT) {
                TrackGroup mediaTrackGroup = group.getMediaTrackGroup();
                for (int i = 0; i < mediaTrackGroup.length; i++) {
                    if (textTrackCounter == targetIndex) {
                        targetGroup = mediaTrackGroup;
                        targetTrackIndex = i;
                        break;
                    }
                    textTrackCounter++;
                }
                if (targetGroup != null) break;
            }
        }

        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
        builder.clearOverridesOfType(C.TRACK_TYPE_TEXT);
        if (targetGroup != null && targetTrackIndex >= 0) {
            builder.setOverrideForType(new TrackSelectionOverride(targetGroup, targetTrackIndex));
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false);
        }
        player.setTrackSelectionParameters(builder.build());
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
        ExoPlayer player = instance;
        if (player != null && player.getCurrentMediaItem() != null) {
            if (player.getPlaybackState() == Player.STATE_IDLE) player.prepare();
            // prepare 的监听器可能释放播放器。
            if (instance == player) player.play();
        }
    }

    public static boolean hasMediaItem() {
        return instance != null && instance.getCurrentMediaItem() != null;
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
