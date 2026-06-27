package com.lm.android.tv.player.data.remote;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * OkHttp 客户端单例
 */
public final class ApiClient {

    private static volatile OkHttpClient instance;

    private ApiClient() {}

    public static OkHttpClient getInstance() {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) {
                    instance = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return instance;
    }
}
