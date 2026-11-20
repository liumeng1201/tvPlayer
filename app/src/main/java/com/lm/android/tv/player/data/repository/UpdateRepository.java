package com.lm.android.tv.player.data.repository;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lm.android.tv.player.data.model.UpdateModel;
import com.lm.android.tv.player.data.remote.ApiClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** 从 GitHub 最新正式发行版读取更新清单。 */
public final class UpdateRepository {
    static final HttpUrl LATEST_RELEASE =
            HttpUrl.get("https://api.github.com/repos/liumeng1201/tvPlayer/releases/latest");
    private static final String ASSET_BASE = "https://github.com/liumeng1201/tvPlayer/releases/download/";
    private static final String PROXY = "https://gh-proxy.com/";

    private final OkHttpClient client;

    public UpdateRepository() {
        this(ApiClient.getInstance());
    }

    UpdateRepository(OkHttpClient client) {
        this.client = client;
    }

    public static String proxyUrl(String githubUrl) {
        return PROXY + githubUrl;
    }

    @Nullable
    public UpdateModel checkUpdate() throws IOException {
        ReleaseAssets release = fetch(LATEST_RELEASE, true, UpdateRepository::readRelease);
        if (release == null) return null;
        return fetch(release.manifest, false, json -> readManifest(json, release));
    }

    private <T> T fetch(HttpUrl url, boolean allowMissing, Function<JsonObject, T> parse) throws IOException {
        try {
            return request(HttpUrl.get(proxyUrl(url.toString())), false, parse);
        } catch (IOException proxyError) {
            try {
                return request(url, allowMissing, parse);
            } catch (IOException directError) {
                directError.addSuppressed(proxyError);
                throw directError;
            }
        }
    }

    private <T> T request(HttpUrl url, boolean allowMissing, Function<JsonObject, T> parse) throws IOException {
        Request request = new Request.Builder().url(url)
                .header("Accept", "application/json")
                .build();
        Call call = client.newCall(request);
        call.timeout().timeout(8, TimeUnit.SECONDS);
        try (Response response = call.execute()) {
            if (allowMissing && response.code() == 404) return null;
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("更新请求失败 HTTP " + response.code());
            }
            try {
                return parse.apply(JsonParser.parseString(body.string()).getAsJsonObject());
            } catch (RuntimeException error) {
                throw new IOException("更新信息格式无效", error);
            }
        }
    }

    private static ReleaseAssets readRelease(JsonObject json) {
        String tag = json.get("tag_name").getAsString();
        if (!tag.matches("v[1-9][0-9]*") || json.get("draft").getAsBoolean()
                || json.get("prerelease").getAsBoolean()) {
            throw new IllegalArgumentException("无效的正式发行版");
        }
        String assetPrefix = ASSET_BASE + tag + "/";
        HttpUrl manifest = readAsset(json, "update.json", assetPrefix);
        HttpUrl apk = readAsset(json, "tvPlayer.apk", assetPrefix);
        return new ReleaseAssets(tag.substring(1), manifest, apk);
    }

    private static HttpUrl readAsset(JsonObject release, String name, String prefix) {
        String expectedUrl = prefix + name;
        for (JsonElement element : release.getAsJsonArray("assets")) {
            JsonObject asset = element.getAsJsonObject();
            if (name.equals(asset.get("name").getAsString())
                    && expectedUrl.equals(asset.get("browser_download_url").getAsString())) {
                return HttpUrl.get(expectedUrl);
            }
        }
        throw new IllegalArgumentException("缺少发行附件 " + name);
    }

    private static UpdateModel readManifest(JsonObject json, ReleaseAssets release) {
        String versionCode = json.get("versionCode").getAsString();
        String versionName = json.get("versionName").getAsString();
        String downloadUrl = json.get("downloadUrl").getAsString();
        if (!release.versionCode.equals(versionCode) || versionName.trim().isEmpty()
                || !release.apk.toString().equals(downloadUrl)) {
            throw new IllegalArgumentException("更新清单与发行版不一致");
        }
        UpdateModel update = new UpdateModel();
        update.versionCode = Integer.parseInt(versionCode);
        update.versionName = versionName;
        update.downloadUrl = downloadUrl;
        return update;
    }

    private static final class ReleaseAssets {
        final String versionCode;
        final HttpUrl manifest;
        final HttpUrl apk;

        ReleaseAssets(String versionCode, HttpUrl manifest, HttpUrl apk) {
            this.versionCode = versionCode;
            this.manifest = manifest;
            this.apk = apk;
        }
    }
}
