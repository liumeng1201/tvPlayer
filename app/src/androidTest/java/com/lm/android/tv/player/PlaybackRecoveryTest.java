package com.lm.android.tv.player;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.lm.android.tv.player.data.db.AppDatabase;
import com.lm.android.tv.player.data.db.VideoProgressEntity;
import com.lm.android.tv.player.data.model.FileModel;
import com.lm.android.tv.player.player.ExoPlayerManager;
import com.lm.android.tv.player.ui.player.PlaybackQueueViewModel;
import com.lm.android.tv.player.ui.player.PlayerFragment;
import com.lm.android.tv.player.util.ParentalControlManager;
import com.lm.android.tv.player.util.SharedPrefsHelper;
import com.lm.android.tv.player.util.Urls;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public class PlaybackRecoveryTest {
    private Context context;
    private MockMediaServer server;
    private String previousUrl;
    private CountDownLatch databaseGate;
    private static final String PATH = "/动画/第一季/";

    @Before public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("player", Context.MODE_PRIVATE).edit().clear().commit();
        previousUrl = Urls.serverUrl;
        server = new MockMediaServer();
        Urls.serverUrl = server.baseUrl();
    }

    @After public void tearDown() throws Exception {
        if (databaseGate != null) databaseGate.countDown();
        if (server.directoryGate != null) server.directoryGate.countDown();
        server.close();
        Urls.serverUrl = previousUrl;
        context.getSharedPreferences("player", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private void open(ActivityScenario<MainActivity> activity, String file, boolean reload) {
        activity.onActivity(a -> {
            FileModel video = new FileModel();
            video.type = 1;
            video.name = file;
            video.url = file;
            new ViewModelProvider(a).get(PlaybackQueueViewModel.class).setVideos(
                    reload ? Collections.emptyList() : Collections.singletonList(video));
            Bundle args = new Bundle();
            args.putString(PlayerFragment.ARG_PATH, PATH);
            args.putInt(PlayerFragment.ARG_INDEX, 0);
            PlayerFragment fragment = new PlayerFragment();
            fragment.setArguments(args);
            a.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment).commitNow();
        });
    }

    private boolean playing(ActivityScenario<MainActivity> activity) {
        AtomicBoolean value = new AtomicBoolean();
        activity.onActivity(a -> value.set(ExoPlayerManager.isPlaying()));
        return value.get();
    }

    @Test public void playbackKeysAndEmptyEndAreIgnoredWhileQueueIsLoading() throws Exception {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            server.directoryGate = new CountDownLatch(1);
            open(activity, "第01集.mp4", true);
            waitFor(() -> server.requested(PATH + "files.json"));
            activity.onActivity(a -> {
                for (int key : new int[]{KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT}) {
                    assertTrue(a.onKeyDown(key, new KeyEvent(KeyEvent.ACTION_DOWN, key)));
                }
                ExoPlayerManager.resume();
                assertFalse(ExoPlayerManager.wantsToPlay());
                assertNull(ExoPlayerManager.getInstance(a).getCurrentMediaItem());
                // An empty timeline can emit ENDED; it must not finish this screen.
                ExoPlayerManager.getInstance(a).prepare();
                assertNotNull(a.findViewById(R.id.player_view));
                assertTrue(a.getSupportFragmentManager().findFragmentById(R.id.fragment_container)
                        instanceof PlayerFragment);
            });
            server.directoryGate.countDown();
            waitFor(() -> playing(activity));
            activity.onActivity(a -> {
                a.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
                assertFalse(ExoPlayerManager.wantsToPlay());
                a.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
            });
            waitFor(() -> playing(activity));
        }
    }

    private void seedProgressAndBlockDatabase() throws Exception {
        String url = Urls.buildEncodedFileUrl(PATH, "第01集.mp4");
        databaseGate = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        AppDatabase.getDbExecutor().execute(() -> {
            AppDatabase.getInstance(context).videoProgressDao()
                    .insertOrUpdate(new VideoProgressEntity(url, 5000, 18000, 1));
            blocked.countDown();
            try {
                if (!databaseGate.await(15, TimeUnit.SECONDS)) throw new AssertionError("Database gate timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        });
        assertTrue(blocked.await(5, TimeUnit.SECONDS));
    }

    @Test public void recreationDuringResumeLookupPreservesDatabaseProgress() throws Exception {
        seedProgressAndBlockDatabase();
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            open(activity, "第01集.mp4", false);
            waitFor(() -> playing(activity));
            activity.onActivity(a -> a.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)));
            activity.recreate();
            activity.recreate();
            databaseGate.countDown();
            waitFor(() -> {
                AtomicBoolean resumed = new AtomicBoolean();
                activity.onActivity(a -> resumed.set(ExoPlayerManager.getCurrentPosition() == 5000
                        && !ExoPlayerManager.wantsToPlay()));
                return resumed.get();
            });
            CountDownLatch drained = new CountDownLatch(1);
            AppDatabase.getDbExecutor().execute(drained::countDown);
            assertTrue(drained.await(5, TimeUnit.SECONDS));
            String url = Urls.buildEncodedFileUrl(PATH, "第01集.mp4");
            assertEquals(5000, AppDatabase.getInstance(context).videoProgressDao()
                    .getProgress(url).getPositionMs());
        }
    }

    @Test public void explicitSeekDuringResumeLookupOverridesOldProgress() throws Exception {
        seedProgressAndBlockDatabase();
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            open(activity, "第01集.mp4", false);
            waitFor(() -> playing(activity));
            activity.onActivity(a -> {
                a.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
                a.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
            });
            activity.recreate();
            databaseGate.countDown();
            CountDownLatch drained = new CountDownLatch(1);
            AppDatabase.getDbExecutor().execute(drained::countDown);
            assertTrue(drained.await(5, TimeUnit.SECONDS));
            activity.onActivity(a -> {
                assertEquals(0, ExoPlayerManager.getCurrentPosition());
                assertFalse(ExoPlayerManager.wantsToPlay());
            });
        }
    }

    @Test public void delayedQueueCannotStartPlaybackInBackground() throws Exception {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            server.directoryGate = new CountDownLatch(1);
            open(activity, "第01集.mp4", true);
            waitFor(() -> server.requested(PATH + "files.json"));
            activity.moveToState(Lifecycle.State.CREATED);
            server.directoryGate.countDown();
            waitFor(() -> {
                AtomicBoolean loaded = new AtomicBoolean();
                activity.onActivity(a -> loaded.set(!new ViewModelProvider(a)
                        .get(PlaybackQueueViewModel.class).getVideos().isEmpty()));
                return loaded.get();
            });
            activity.onActivity(a -> {
                assertFalse(ExoPlayerManager.wantsToPlay());
                assertNull(ExoPlayerManager.getInstance(a).getCurrentMediaItem());
            });
            activity.moveToState(Lifecycle.State.RESUMED);
            waitFor(() -> playing(activity));
        }
    }

    @Test public void playbackErrorRecordsRestAndResetsBudgetAfterTwoHours() throws Exception {
        SharedPrefsHelper.putLong(context, SharedPrefsHelper.KEY_ACCUMULATED_MS, 60000);
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            open(activity, "missing.mp4", false);
            waitFor(() -> SharedPrefsHelper.getLong(context,
                    SharedPrefsHelper.KEY_PLAYBACK_PAUSED_AT, 0) > 0);
            assertEquals(60000, ParentalControlManager.getAccumulatedMs(context));
            SharedPrefsHelper.putLong(context, SharedPrefsHelper.KEY_PLAYBACK_PAUSED_AT,
                    System.currentTimeMillis() - SharedPrefsHelper.COOLDOWN_MS);
            activity.onActivity(a -> {
                a.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
                assertEquals(0, ParentalControlManager.getAccumulatedMs(context));
            });
        }
    }

    @Test public void playbackErrorAtLimitBlocksRetry() throws Exception {
        SharedPrefsHelper.putLong(context, SharedPrefsHelper.KEY_ACCUMULATED_MS,
                SharedPrefsHelper.SESSION_MAX_MS);
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            open(activity, "missing.mp4", false);
            waitFor(() -> ParentalControlManager.isInCooldown(context));
            activity.onActivity(a -> {
                a.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
                assertFalse(ExoPlayerManager.wantsToPlay());
            });
        }
    }

    @Test public void recreationKeepsPausedPositionNearEnd() throws Exception {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            open(activity, "第01集.mp4", false);
            waitFor(() -> playing(activity));
            activity.onActivity(a -> {
                ExoPlayer player = ExoPlayerManager.getInstance(a);
                a.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
                player.seekTo(player.getDuration() - 3000);
            });
            activity.recreate();
            activity.onActivity(a -> {
                ExoPlayer player = ExoPlayerManager.getInstance(a);
                assertFalse(player.getPlayWhenReady());
                assertTrue("Should keep the position in the final ten seconds",
                        player.getCurrentPosition() >= 8000);
            });
        }
    }

    @Test public void restartingThenRecreatingDoesNotRestoreOldDatabaseProgress() throws Exception {
        String url = Urls.buildEncodedFileUrl(PATH, "第01集.mp4");
        CountDownLatch saved = new CountDownLatch(1);
        AppDatabase.getDbExecutor().execute(() -> {
            AppDatabase.getInstance(context).videoProgressDao()
                    .insertOrUpdate(new VideoProgressEntity(url, 1000, 90000, 1));
            saved.countDown();
        });
        assertTrue(saved.await(5, TimeUnit.SECONDS));
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            open(activity, "第01集.mp4", false);
            waitFor(() -> {
                AtomicBoolean hint = new AtomicBoolean();
                activity.onActivity(a -> hint.set(a.findViewById(R.id.tv_restart_hint)
                        .getVisibility() == android.view.View.VISIBLE));
                return hint.get();
            });
            activity.onActivity(a -> {
                a.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
                a.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
            });
            activity.recreate();
            activity.onActivity(a -> {
                assertEquals(0, ExoPlayerManager.getCurrentPosition());
                assertFalse(ExoPlayerManager.wantsToPlay());
            });
        }
    }

    @Test public void listLoopWithOnlyOneVideoRestartsIt() throws Exception {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            open(activity, "第01集.mp4", false);
            waitFor(() -> playing(activity));
            activity.onActivity(a -> {
                ExoPlayer player = ExoPlayerManager.getInstance(a);
                player.seekTo(player.getDuration() - 250);
            });
            waitFor(() -> {
                AtomicBoolean looped = new AtomicBoolean();
                activity.onActivity(a -> looped.set(a.findViewById(R.id.player_view) != null
                        && ExoPlayerManager.isPlaying() && ExoPlayerManager.getCurrentPosition() < 3000));
                return looped.get();
            });
        }
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < until) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(100);
        }
        fail("Timed out waiting for playback recovery");
    }
}
