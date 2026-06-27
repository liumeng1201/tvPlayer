package com.lm.android.tv.player.data.repository;

import com.lm.android.tv.player.data.remote.ApiService;

import java.io.IOException;

/**
 * 文件数据仓库
 */
public final class FileRepository {

    public interface FileCallback {
        void onSuccess(String json);
        void onError(IOException e);
    }

    public void loadFiles(String path, final FileCallback callback) {
        ApiService.fetchFiles(path, new ApiService.ApiCallback<String>() {
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
