package com.lm.android.tv.player;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.lm.android.tv.player.data.db.AppDatabase;
import com.lm.android.tv.player.data.db.VideoProgressEntity;
import com.lm.android.tv.player.util.ParentalControlManager;
import com.lm.android.tv.player.util.SharedPrefsHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StorageTest {
    private Context context;
    private AppDatabase database;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("player", Context.MODE_PRIVATE).edit().clear().commit();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
    }

    @After
    public void tearDown() {
        database.close();
        context.getSharedPreferences("player", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void progressCanBeSavedUpdatedAndDeleted() {
        String url = "http://example.test/video.mp4";
        database.videoProgressDao().insertOrUpdate(new VideoProgressEntity(url, 12000, 90000, 1));
        assertEquals(12000, database.videoProgressDao().getProgress(url).getPositionMs());
        database.videoProgressDao().insertOrUpdate(new VideoProgressEntity(url, 24000, 90000, 2));
        assertEquals(24000, database.videoProgressDao().getProgress(url).getPositionMs());
        database.videoProgressDao().delete(url);
        assertNull(database.videoProgressDao().getProgress(url));
    }

    @Test
    public void playbackLimitAndCooldownPersistAndExpire() {
        ParentalControlManager.addAccumulatedTime(context, SharedPrefsHelper.SESSION_MAX_MS - 1);
        assertFalse(ParentalControlManager.isLimitReached(context));
        ParentalControlManager.addAccumulatedTime(context, 1);
        assertTrue(ParentalControlManager.isLimitReached(context));
        ParentalControlManager.startCooldown(context);
        assertTrue(ParentalControlManager.isInCooldown(context));
        assertEquals(0, ParentalControlManager.getAccumulatedMs(context));
        SharedPrefsHelper.putLong(context, SharedPrefsHelper.KEY_COOLDOWN_UNTIL, System.currentTimeMillis() - 1);
        assertFalse(ParentalControlManager.isInCooldown(context));
        assertEquals(0, SharedPrefsHelper.getLong(context, SharedPrefsHelper.KEY_COOLDOWN_UNTIL, -1));
    }
}
