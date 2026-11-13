package com.lm.android.tv.player;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.View;
import androidx.media3.common.text.Cue;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.lm.android.tv.player.data.model.FileModel;
import com.lm.android.tv.player.player.ExoPlayerManager;
import com.lm.android.tv.player.ui.widget.FileGridAdapter;
import com.lm.android.tv.player.util.Urls;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@RunWith(AndroidJUnit4.class)
public class MediaLibraryTest {
    @Test
    public void browsesChineseDirectoriesAndPlaysVideoOverHttp() throws Exception {
        String previousUrl = Urls.serverUrl;
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("player", Context.MODE_PRIVATE).edit().clear().commit();
        try (MockMediaServer server = new MockMediaServer()) {
            Urls.serverUrl = server.baseUrl();
            try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
                waitFor("root directory", () -> gridHas(activity, 3, "动画"));
                clickFirstItem(activity);
                waitFor("animation directory", () -> gridHas(activity, 1, "第一季"));
                clickFirstItem(activity);
                waitFor("season directory", () -> gridHas(activity, 2, "第01集"));
                clickFirstItem(activity);
                waitFor("MP4 playback", () -> {
                    AtomicBoolean playingVideo = new AtomicBoolean();
                    activity.onActivity(a -> {
                        ExoPlayer player = ExoPlayerManager.getInstance(a);
                        playingVideo.set(a.findViewById(R.id.player_view) != null
                                && player.isPlaying() && player.getVideoFormat() != null
                                && player.getCurrentPosition() > 200);
                    });
                    return playingVideo.get();
                });
                assertTrue(server.requested("/动画/files.json"));
                assertTrue(server.requested("/动画/第一季/files.json"));
                assertTrue(server.requested("/动画/第一季/第01集.mp4"));
                waitFor("first episode subtitle", () -> {
                    AtomicBoolean hasSubtitle = new AtomicBoolean();
                    activity.onActivity(a -> {
                        ExoPlayer player = ExoPlayerManager.getInstance(a);
                        PlayerView playerView = a.findViewById(R.id.player_view);
                        if (playerView == null || playerView.getPlayer() != player
                                || playerView.getSubtitleView() == null
                                || playerView.getSubtitleView().getVisibility() != View.VISIBLE) return;
                        for (Cue cue : player.getCurrentCues().cues) {
                            if (cue.text != null && cue.text.toString().contains("欢迎来到模拟影院")) {
                                hasSubtitle.set(true);
                            }
                        }
                    });
                    return hasSubtitle.get();
                });
                assertTrue(server.requested("/动画/第一季/第01集.srt"));
                activity.onActivity(a -> {
                    ExoPlayer player = ExoPlayerManager.getInstance(a);
                    assertEquals(1, player.getCurrentMediaItem().localConfiguration.subtitleConfigurations.size());
                    player.seekTo(player.getDuration() - 300);
                });
                waitFor("second episode without subtitles", () -> {
                    AtomicBoolean correct = new AtomicBoolean();
                    activity.onActivity(a -> {
                        ExoPlayer player = ExoPlayerManager.getInstance(a);
                        if (player.getCurrentMediaItem() == null
                                || player.getCurrentMediaItem().localConfiguration == null) return;
                        android.net.Uri uri = player.getCurrentMediaItem().localConfiguration.uri;
                        correct.set(uri.toString().contains("%E7%AC%AC02%E9%9B%86.mp4")
                                && player.isPlaying()
                                && player.getCurrentMediaItem().localConfiguration.subtitleConfigurations.isEmpty()
                                && player.getCurrentCues().cues.isEmpty());
                    });
                    return correct.get();
                });
                activity.onActivity(MainActivity::onBackPressed);
                waitFor("return to season", () -> gridHas(activity, 2, "第01集"));
                activity.onActivity(MainActivity::onBackPressed);
                waitFor("return to animation", () -> gridHas(activity, 1, "第一季"));
                activity.onActivity(MainActivity::onBackPressed);
                waitFor("return to root", () -> gridHas(activity, 3, "动画"));
            }
        } finally {
            Urls.serverUrl = previousUrl;
            context.getSharedPreferences("player", Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    @Test
    public void servesRealImageAndSupportsVideoByteRanges() throws Exception {
        try (MockMediaServer server = new MockMediaServer()) {
            OkHttpClient client = new OkHttpClient();
            Request image = new Request.Builder().url(server.baseUrl() + "/相册/蓝天.png").build();
            try (Response response = client.newCall(image).execute()) {
                assertEquals(200, response.code());
                assertEquals("image/png", response.header("Content-Type"));
                assertTrue(response.body().bytes().length > 0);
            }
            Request range = new Request.Builder().url(server.baseUrl() + "/动画/第一季/第01集.mp4")
                    .header("Range", "bytes=100-199").build();
            try (Response response = client.newCall(range).execute()) {
                assertEquals(206, response.code());
                assertTrue(response.header("Content-Range").startsWith("bytes 100-199/"));
                assertEquals(100, response.body().bytes().length);
            }
        }
    }

    private static boolean gridHas(ActivityScenario<MainActivity> activity, int count, String firstName) {
        AtomicBoolean matches = new AtomicBoolean();
        activity.onActivity(a -> {
            RecyclerView grid = a.findViewById(R.id.rv_grid);
            if (grid == null || !(grid.getAdapter() instanceof FileGridAdapter)) return;
            FileGridAdapter adapter = (FileGridAdapter) grid.getAdapter();
            FileModel first = adapter.getItem(0);
            matches.set(adapter.getItemCount() == count && first != null && firstName.equals(first.name)
                    && grid.findViewHolderForAdapterPosition(0) != null);
        });
        return matches.get();
    }

    private static void clickFirstItem(ActivityScenario<MainActivity> activity) {
        activity.onActivity(a -> {
            RecyclerView grid = a.findViewById(R.id.rv_grid);
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(0);
            assertNotNull(holder);
            holder.itemView.performClick();
        });
    }

    private static void waitFor(String what, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(100);
        }
        fail("Timed out waiting for " + what);
    }
}
