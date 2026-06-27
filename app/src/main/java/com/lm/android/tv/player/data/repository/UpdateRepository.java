package com.lm.android.tv.player.data.repository;

import com.lm.android.tv.player.data.remote.ApiService;

import java.io.IOException;

/**
 * 版本更新数据仓库
 */
public final class UpdateRepository {

    public interface UpdateCallback {
        void onSuccess(String json);
        void onError(IOException e);
    }

    public void checkUpdate(final UpdateCallback callback) {
        ApiService.fetchUpdateInfo(new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String data) {
                if (callback != null) {
                    callback.onSuccess(data);
                }
            }

            @Override
            public void onError(IOException e) {
                if (callback != null) {
                    callback.onError(e);
                }
            }
        });
    }
}
