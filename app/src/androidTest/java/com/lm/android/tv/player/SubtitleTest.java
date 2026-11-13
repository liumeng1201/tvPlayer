package com.lm.android.tv.player;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.text.subrip.SubripParser;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.lm.android.tv.player.player.ExoPlayerManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@RunWith(AndroidJUnit4.class)
public class SubtitleTest {
    @Test
    @OptIn(markerClass = UnstableApi.class)
    public void subtitleFileContainsBothTimedCues() throws Exception {
        try (MockMediaServer server = new MockMediaServer()) {
            Request request = new Request.Builder()
                    .url(server.baseUrl() + "/动画/第一季/第01集.srt").build();
            try (Response response = new OkHttpClient().newCall(request).execute()) {
                assertEquals(200, response.code());
                List<String> cues = new ArrayList<>();
                new SubripParser().parse(response.body().bytes(), SubtitleParser.OutputOptions.allCues(),
                        group -> cues.add(group.cues.get(0).text.toString()));
                assertEquals(2, cues.size());
                assertEquals("欢迎来到模拟影院", cues.get(0));
                assertEquals("这是第二段测试字幕", cues.get(1));
            }
        }
    }

    @Test
    public void loadsChineseSrtAndShowsCorrectCueAfterSeek() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        try (MockMediaServer server = new MockMediaServer()) {
            CountDownLatch firstCue = new CountDownLatch(1);
            CountDownLatch secondCue = new CountDownLatch(1);
            AtomicReference<PlaybackException> failure = new AtomicReference<>();
            StringBuilder observed = new StringBuilder();
            try {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    ExoPlayer player = ExoPlayerManager.getInstance(context);
                    player.addListener(new Player.Listener() {
                        @Override public void onCues(CueGroup group) {
                            for (Cue cue : group.cues) {
                                if (cue.text == null) continue;
                                observed.append('[').append(player.getCurrentPosition()).append(':')
                                        .append(cue.text).append(']');
                                if (cue.text.toString().contains("欢迎来到模拟影院")) firstCue.countDown();
                                if (cue.text.toString().contains("这是第二段测试字幕")) secondCue.countDown();
                            }
                        }
                        @Override public void onPlayerError(PlaybackException error) {
                            failure.set(error);
                            firstCue.countDown();
                            secondCue.countDown();
                        }
                    });
                    MediaItem.SubtitleConfiguration subtitle = new MediaItem.SubtitleConfiguration.Builder(
                            Uri.parse(server.baseUrl() + "/动画/第一季/第01集.srt"))
                            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                            .setLanguage("zh")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build();
                    MediaItem media = new MediaItem.Builder()
                            .setUri(server.baseUrl() + "/动画/第一季/第01集.mp4")
                            .setSubtitleConfigurations(Collections.singletonList(subtitle))
                            .build();
                    player.setPlayWhenReady(true);
                    player.setMediaItem(media);
                    player.prepare();
                    player.seekTo(2000);
                });
                assertTrue("First subtitle did not appear", firstCue.await(20, TimeUnit.SECONDS));
                assertNull("Playback failed: " + failure.get(), failure.get());
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                        ExoPlayerManager.getInstance(context).seekTo(7000));
                boolean gotSecondCue = secondCue.await(20, TimeUnit.SECONDS);
                AtomicReference<String> state = new AtomicReference<>();
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    ExoPlayer player = ExoPlayerManager.getInstance(context);
                    state.set("position=" + player.getCurrentPosition() + ", duration=" + player.getDuration()
                            + ", state=" + player.getPlaybackState() + ", isPlaying=" + player.isPlaying()
                            + ", cues=" + observed);
                });
                assertTrue("Subtitle did not update after seeking: " + state.get(), gotSecondCue);
                assertNull("Playback failed: " + failure.get(), failure.get());
                assertTrue("Subtitle was not requested", server.requested("/动画/第一季/第01集.srt"));
            } finally {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(ExoPlayerManager::release);
            }
        }
    }
}
