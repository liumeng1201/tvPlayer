package com.lm.android.tv.player.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.lm.android.tv.player.data.model.UpdateModel;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class UpdateRepositoryTest {
    private static final String LATEST = UpdateRepository.LATEST_RELEASE.toString();
    private static final String MANIFEST =
            "https://github.com/liumeng1201/tvPlayer/releases/download/v340/update.json";
    private static final String APK = MANIFEST.replace("update.json", "tvPlayer.apk");
    private static final String RELEASE = "{\"tag_name\":\"v340\",\"draft\":false,\"prerelease\":false,"
            + "\"assets\":[" + asset("update.json", MANIFEST) + "," + asset("tvPlayer.apk", APK) + "]}";
    private static final String UPDATE =
            "{\"versionName\":\"3.0\",\"versionCode\":340,\"downloadUrl\":\"" + APK + "\"}";

    private final List<String> requests = new ArrayList<>();

    @Test
    public void readsBothReleaseAndManifestThroughTheProxy() throws IOException {
        UpdateModel update = repository(200, RELEASE, 200, UPDATE).checkUpdate();

        assertEquals("3.0", update.versionName);
        assertEquals(340, update.versionCode);
        assertEquals(APK, update.downloadUrl);
        assertEquals(Arrays.asList(proxy(LATEST), proxy(MANIFEST)), requests);
    }

    @Test
    public void fallsBackIndependentlyForRateLimitedReleaseAndInvalidProxiedManifest() throws IOException {
        UpdateModel update = repository(403, "rate limited", 200, RELEASE,
                200, UPDATE.replace("340", "339"), 200, UPDATE).checkUpdate();

        assertEquals(340, update.versionCode);
        assertEquals(Arrays.asList(proxy(LATEST), LATEST, proxy(MANIFEST), MANIFEST), requests);
    }

    @Test
    public void confirmsNoReleaseWithGitHubDirectly() throws IOException {
        assertNull(repository(404, "", 404, "").checkUpdate());
        assertEquals(Arrays.asList(proxy(LATEST), LATEST), requests);
    }

    @Test
    public void rejectsAManifestThatDoesNotPointToTheReleaseApk() {
        String mismatch = UPDATE.replace(APK, "https://example.com/other.apk");
        UpdateRepository repository = repository(200, RELEASE, 200, mismatch, 200, mismatch);

        assertThrows(IOException.class, repository::checkUpdate);
    }

    private UpdateRepository repository(Object... responses) {
        return new UpdateRepository(new OkHttpClient.Builder().addInterceptor(chain -> {
            int next = requests.size() * 2;
            requests.add(chain.request().url().toString());
            return new Response.Builder().request(chain.request())
                    .code((int) responses[next]).message("test")
                    .protocol(Protocol.HTTP_1_1)
                    .body(ResponseBody.create((String) responses[next + 1], null))
                    .build();
        }).build());
    }

    private static String proxy(String url) {
        return "https://gh-proxy.com/" + url;
    }

    private static String asset(String name, String url) {
        return "{\"name\":\"" + name + "\",\"browser_download_url\":\"" + url + "\"}";
    }
}
