package com.lm.android.tv.player.data.repository;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lm.android.tv.player.data.model.FileModel;
import com.lm.android.tv.player.data.remote.ApiService;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import okhttp3.Call;

/**
 * 文件数据仓库
 */
public final class FileRepository {
    private static final Gson GSON = new Gson();
    private static final Type FILE_LIST = new TypeToken<List<FileModel>>() {}.getType();

    public interface FileCallback {
        void onSuccess(List<FileModel> files);
        void onError(IOException e);
    }

    public Call loadFiles(String path, FileCallback callback) {
        return ApiService.fetchFiles(path, new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String data) {
                List<FileModel> files;
                try {
                    files = parseFiles(data);
                } catch (RuntimeException e) {
                    callback.onError(new IOException("目录数据格式错误", e));
                    return;
                }
                callback.onSuccess(files);
            }

            @Override
            public void onError(IOException e) {
                callback.onError(e);
            }
        });
    }

    public static List<FileModel> parseFiles(String data) {
        List<FileModel> files = GSON.fromJson(data, FILE_LIST);
        if (files == null) throw new IllegalArgumentException("目录数据为空");
        for (FileModel file : files) {
            if (file == null || isBlank(file.name) || file.type < 0 || file.type > 2
                    || (file.isDirectory() && (file.name.contains("/") || !isRelativePath(file.name)))
                    || (!file.isDirectory() && !isRelativePath(file.url))
                    || (!isBlank(file.cover) && !isRelativePath(file.cover))
                    || (!isBlank(file.subtitle) && !isRelativePath(file.subtitle))) {
                throw new IllegalArgumentException("目录条目无效");
            }
        }
        return files;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isRelativePath(String path) {
        if (isBlank(path) || path.contains("\\") || path.contains("://")) return false;
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) return false;
        }
        return true;
    }
}
