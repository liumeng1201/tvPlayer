package com.lm.android.tv.player;

import static org.junit.Assert.*;

import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.lm.android.tv.player.util.Urls;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@RunWith(AndroidJUnit4.class)
public class HomeScreenTest {
    @Test
    public void loadsFilesAndNavigatesToSettingsAndBack() throws Exception {
        String previousUrl = Urls.serverUrl;
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    if (request.getPath().equals("/files.json")) {
                        return new MockResponse().setBody("[{\"name\":\"动画\",\"type\":0}]");
                    }
                    return new MockResponse().setResponseCode(404);
                }
            });
            server.start();
            Urls.serverUrl = server.url("/").toString().replaceAll("/$", "");
            try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
                AtomicInteger count = new AtomicInteger();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (System.nanoTime() < deadline) {
                    activity.onActivity(a -> {
                        RecyclerView grid = a.findViewById(R.id.rv_grid);
                        if (grid != null && grid.getAdapter() != null) count.set(grid.getAdapter().getItemCount());
                    });
                    if (count.get() == 1) break;
                    Thread.sleep(100);
                }
                assertEquals("Directory response should populate the grid", 1, count.get());
                activity.onActivity(a -> {
                    View settingsButton = a.findViewById(R.id.btn_setting);
                    assertTrue(settingsButton.getBackground().isStateful());
                    settingsButton.performClick();
                });
                activity.onActivity(a -> assertNotNull(a.findViewById(R.id.rv_settings)));
                AtomicInteger focusableSettings = new AtomicInteger();
                long settingsDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (System.nanoTime() < settingsDeadline) {
                    activity.onActivity(a -> {
                        RecyclerView list = a.findViewById(R.id.rv_settings);
                        if (list != null && list.getChildCount() > 0) {
                            View item = list.getChildAt(0);
                            if (item.isFocusable() && item.getBackground().isStateful()) {
                                focusableSettings.set(1);
                            }
                        }
                    });
                    if (focusableSettings.get() == 1) break;
                    Thread.sleep(100);
                }
                assertEquals("Settings should have a focus background", 1, focusableSettings.get());
                activity.onActivity(MainActivity::onBackPressed);
                activity.onActivity(a -> {
                    View grid = a.findViewById(R.id.rv_grid);
                    assertNotNull(grid);
                });
            }
        } finally {
            Urls.serverUrl = previousUrl;
        }
    }
}
