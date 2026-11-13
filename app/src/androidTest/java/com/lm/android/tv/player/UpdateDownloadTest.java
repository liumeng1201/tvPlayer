package com.lm.android.tv.player;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.lm.android.tv.player.ui.setting.SettingFragment;
import com.lm.android.tv.player.util.Urls;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public class UpdateDownloadTest {
    @Test
    public void failedDownloadBroadcastCleansUpReceiver() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        if (Build.VERSION.SDK_INT <= 28 && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                    "pm grant " + context.getPackageName() + " android.permission.WRITE_EXTERNAL_STORAGE").close();
        }
        String oldUrl = Urls.serverUrl;
        AtomicLong downloadId = new AtomicLong(-1);
        try (MockMediaServer server = new MockMediaServer()) {
            String baseUrl = server.baseUrl();
            Urls.serverUrl = baseUrl;
            try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
                waitFor(() -> {
                    AtomicBoolean ready = new AtomicBoolean();
                    activity.onActivity(a -> {
                        View button = a.findViewById(R.id.btn_setting);
                        ready.set(button != null && button.getVisibility() == View.VISIBLE);
                    });
                    return ready.get();
                });
                activity.onActivity(a -> a.findViewById(R.id.btn_setting).performClick());
                waitFor(() -> {
                    AtomicBoolean ready = new AtomicBoolean();
                    activity.onActivity(a -> ready.set(a.getSupportFragmentManager()
                            .findFragmentById(R.id.fragment_container) instanceof SettingFragment));
                    return ready.get();
                });
                Method download = SettingFragment.class.getDeclaredMethod("downloadApk", String.class);
                Field idField = SettingFragment.class.getDeclaredField("activeDownloadId");
                Field receiverField = SettingFragment.class.getDeclaredField("downloadReceiver");
                download.setAccessible(true);
                idField.setAccessible(true);
                receiverField.setAccessible(true);
                activity.onActivity(a -> {
                    try {
                        Fragment fragment = a.getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                        download.invoke(fragment, baseUrl + "/missing.apk");
                        downloadId.set(idField.getLong(fragment));
                        assertTrue("Download should be enqueued", downloadId.get() > 0);
                        assertNotNull(receiverField.get(fragment));
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError(e);
                    }
                });
                waitFor(() -> {
                    AtomicBoolean finished = new AtomicBoolean();
                    activity.onActivity(a -> {
                        try {
                            Fragment fragment = a.getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                            finished.set(receiverField.get(fragment) == null && idField.getLong(fragment) == -1);
                        } catch (IllegalAccessException e) {
                            throw new AssertionError(e);
                        }
                    });
                    return finished.get();
                });
            }
        } finally {
            if (downloadId.get() > 0) {
                ((DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE)).remove(downloadId.get());
            }
            Urls.serverUrl = oldUrl;
        }
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(100);
        }
        fail("Timed out waiting for update download or receiver cleanup");
    }
}
