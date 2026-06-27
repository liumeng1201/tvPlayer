package com.lm.android.tv.player.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/**
 * 视频进度 DAO
 */
@Dao
public interface VideoProgressDao {

    /** 获取某个视频的进度记录（包含位置和总时长） */
    @Query("SELECT * FROM video_progress WHERE videoUrl = :videoUrl LIMIT 1")
    VideoProgressEntity getProgress(String videoUrl);

    /** 插入或覆盖进度 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(VideoProgressEntity entity);

    /** 删除某条进度（播放完成时调用） */
    @Query("DELETE FROM video_progress WHERE videoUrl = :videoUrl")
    void delete(String videoUrl);
}
