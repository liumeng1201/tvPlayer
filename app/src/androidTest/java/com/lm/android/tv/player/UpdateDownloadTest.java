package com.lm.android.tv.player;

import static org.junit.Assert.*;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.lm.android.tv.player.ui.setting.SettingFragment;
import com.lm.android.tv.player.util.Urls;
import com.lm.android.tv.player.util.SharedPrefsHelper;

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

    @Test
    public void returningToSettingsProcessesDownloadFinishedWhileAway() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String oldUrl = Urls.serverUrl;
        AtomicLong downloadId = new AtomicLong(-1);
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        SharedPrefsHelper.putLong(context, SharedPrefsHelper.KEY_UPDATE_DOWNLOAD_ID, -1);
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
                download.setAccessible(true);
                idField.setAccessible(true);
                activity.onActivity(a -> {
                    try {
                        Fragment fragment = a.getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                        download.invoke(fragment, baseUrl + "/missing.apk");
                        downloadId.set(idField.getLong(fragment));
                        assertTrue(downloadId.get() > 0);
                        a.onBackPressed();
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError(e);
                    }
                });
                waitFor(() -> {
                    AtomicBoolean home = new AtomicBoolean();
                    activity.onActivity(a -> home.set(a.findViewById(R.id.btn_setting) != null));
                    return home.get();
                });
                waitFor(() -> {
                    try (Cursor cursor = dm.query(new DownloadManager.Query().setFilterById(downloadId.get()))) {
                        return cursor != null && cursor.moveToFirst()
                                && cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                                == DownloadManager.STATUS_FAILED;
                    }
                });
                assertEquals(downloadId.get(), SharedPrefsHelper.getLong(context,
                        SharedPrefsHelper.KEY_UPDATE_DOWNLOAD_ID, -1));
                activity.onActivity(a -> a.findViewById(R.id.btn_setting).performClick());
                waitFor(() -> SharedPrefsHelper.getLong(context, SharedPrefsHelper.KEY_UPDATE_DOWNLOAD_ID, -1) == -1);
            }
        } finally {
            if (downloadId.get() > 0) dm.remove(downloadId.get());
            SharedPrefsHelper.putLong(context, SharedPrefsHelper.KEY_UPDATE_DOWNLOAD_ID, -1);
            Urls.serverUrl = oldUrl;
        }
    }

    @Test
    public void pendingUpdateCanBeContinuedCancelledAndDownloadedAgain() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPrefsHelper.putLong(context, SharedPrefsHelper.KEY_UPDATE_DOWNLOAD_ID, -1);
        AtomicLong firstId = new AtomicLong(-1);
        AtomicLong secondId = new AtomicLong(-1);
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        try (MockMediaServer server = new MockMediaServer();
             ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            String downloadUrl = server.baseUrl() + "/missing.apk";
            Method download = SettingFragment.class.getDeclaredMethod("downloadApk", String.class);
            Method pending = SettingFragment.class.getDeclaredMethod("showPendingUpdateDialog");
            Field asked = SettingFragment.class.getDeclaredField("installPermissionRequested");
            download.setAccessible(true);
            pending.setAccessible(true);
            asked.setAccessible(true);
            java.util.concurrent.atomic.AtomicReference<SettingFragment> target =
                    new java.util.concurrent.atomic.AtomicReference<>();
            activity.onActivity(a -> {
                SettingFragment fragment = new SettingFragment();
                target.set(fragment);
                a.getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment).commitNow();
                try {
                    download.invoke(fragment, downloadUrl);
                    firstId.set(SharedPrefsHelper.getLong(context,
                            SharedPrefsHelper.KEY_UPDATE_DOWNLOAD_ID, -1));
                    assertTrue(firstId.get() > 0);
                    asked.setBoolean(fragment, true);
                    android.app.AlertDialog dialog = (android.app.AlertDialog) pending.invoke(fragment);
                    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            // Dialog listeners run through the main message queue after the click.
            activity.onActivity(a -> {
                try {
                    assertFalse("An explicit retry must permit another authorization request",
                            asked.getBoolean(target.get()));
                    assertEquals(firstId.get(), SharedPrefsHelper.getLong(context,
                            SharedPrefsHelper.KEY_UPDATE_DOWNLOAD_ID, -1));
                    android.app.AlertDialog dialog = (android.app.AlertDialog) pending.invoke(target.get());
                    dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            activity.onActivity(a -> {
                try {
                    assertEquals(-1, SharedPrefsHelper.getLong(context,
                            SharedPrefsHelper.KEY_UPDATE_DOWNLOAD_ID, -1));
                    download.invoke(target.get(), downloadUrl);
                    secondId.set(SharedPrefsHelper.getLong(context,
                            SharedPrefsHelper.KEY_UPDATE_DOWNLOAD_ID, -1));
                    assertTrue(secondId.get() > 0);
                    assertNotEquals(firstId.get(), secondId.get());
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
        } finally {
            if (firstId.get() > 0) dm.remove(firstId.get());
            if (secondId.get() > 0) dm.remove(secondId.get());
            SharedPrefsHelper.putLong(context, SharedPrefsHelper.KEY_UPDATE_DOWNLOAD_ID, -1);
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
