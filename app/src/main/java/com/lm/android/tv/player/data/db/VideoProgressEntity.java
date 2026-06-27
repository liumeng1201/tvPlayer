package com.lm.android.tv.player.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 视频播放进度记录
 */
@Entity(tableName = "video_progress")
public class VideoProgressEntity {

    @PrimaryKey
    @NonNull
    private String videoUrl;
    private long positionMs;
    private long durationMs;
    private long lastUpdated;

    public VideoProgressEntity() {}

    @Ignore
    public VideoProgressEntity(String videoUrl, long positionMs, long durationMs, long lastUpdated) {
        this.videoUrl = videoUrl;
        this.positionMs = positionMs;
        this.durationMs = durationMs;
        this.lastUpdated = lastUpdated;
    }

    @NonNull
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(@NonNull String videoUrl) { this.videoUrl = videoUrl; }

    public long getPositionMs() { return positionMs; }
    public void setPositionMs(long positionMs) { this.positionMs = positionMs; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
}
