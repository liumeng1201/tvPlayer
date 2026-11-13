package com.lm.android.tv.player.data.remote;

import android.util.Log;

import com.lm.android.tv.player.util.Urls;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

/**
 * HTTP API 服务层
 */
public final class ApiService {

    private static final String TAG = "ApiService";

    private ApiService() {}

    /**
     * 获取目录下的文件列表（异步回调）
     */
    public static void fetchFiles(String path, final ApiCallback<String> callback) {
        try {
            get(Urls.buildEncodedFileUrl(path, "files.json"), callback);
        } catch (IllegalArgumentException e) {
            if (callback != null) callback.onError(new IOException("服务器地址无效", e));
        }
    }

    /**
     * 获取更新信息
     */
    public static void fetchUpdateInfo(final ApiCallback<String> callback) {
        get(Urls.serverUrl + "/update.json", callback);
    }

    private static void get(String url, final ApiCallback<String> callback) {
        Log.d(TAG, "GET: " + url);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        ApiClient.getInstance().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Request failed: " + url, e);
                if (callback != null) {
                    callback.onError(e);
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body;
                try (Response result = response) {
                    if (!result.isSuccessful()) {
                        if (callback != null) callback.onError(new IOException("HTTP " + result.code()));
                        return;
                    }
                    body = result.body() != null ? result.body().string() : null;
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                    return;
                }
                if (callback != null) {
                    callback.onSuccess(body);
                }
            }
        });
    }

    /**
     * 通用异步回调接口
     */
    public interface ApiCallback<T> {
        void onSuccess(T data);
        void onError(IOException e);
    }
}
