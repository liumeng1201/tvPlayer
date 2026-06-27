package com.lm.android.tv.player.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 视频进度数据库
 */
@Database(entities = {VideoProgressEntity.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract VideoProgressDao videoProgressDao();

    private static volatile AppDatabase instance;
    private static final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "player.db"
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return instance;
    }

    /** 获取数据库专用线程池，用于在主线程外执行 DAO 调用 */
    public static ExecutorService getDbExecutor() {
        return dbExecutor;
    }
}
