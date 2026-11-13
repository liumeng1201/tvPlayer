package com.lm.android.tv.player;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.lm.android.tv.player.player.ExoPlayerManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class PlaybackTest {
    @Test
    public void playerPreparesPausesAndSeeksLocalMedia() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File audio = new File(context.getCacheDir(), "test-silence.wav");
        writeSilentWave(audio);
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<PlaybackException> failure = new AtomicReference<>();
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                ExoPlayerManager.getInstance(context).addListener(new Player.Listener() {
                    @Override public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_READY) ready.countDown();
                    }
                    @Override public void onPlayerError(PlaybackException error) {
                        failure.set(error);
                        ready.countDown();
                    }
                });
                ExoPlayerManager.play(context, Uri.fromFile(audio).toString());
            });
            assertTrue("Player did not become ready", ready.await(20, TimeUnit.SECONDS));
            assertNull("Playback failed: " + failure.get(), failure.get());
            AtomicLong position = new AtomicLong();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                ExoPlayerManager.pause();
                ExoPlayerManager.seekTo(5000);
                position.set(ExoPlayerManager.getCurrentPosition());
                assertFalse(ExoPlayerManager.isPlaying());
            });
            assertTrue("Seek should move close to five seconds", position.get() >= 4900);
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(ExoPlayerManager::release);
            audio.delete();
        }
    }

    private static void writeSilentWave(File file) throws Exception {
        int sampleRate = 8000;
        int dataSize = sampleRate * 2 * 10;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + dataSize);
        header.put(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '}).putInt(16);
        header.putShort((short) 1).putShort((short) 1).putInt(sampleRate);
        header.putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16);
        header.put(new byte[]{'d', 'a', 't', 'a'}).putInt(dataSize);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(header.array());
            out.write(new byte[dataSize]);
        }
    }
}
