package com.lm.android.tv.player.ui.main;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lm.android.tv.player.data.model.FileModel;
import com.lm.android.tv.player.data.repository.FileRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 主界面 ViewModel —— 管理文件列表数据
 */
public class MainViewModel extends AndroidViewModel {

    private final FileRepository fileRepository = new FileRepository();
    private final MutableLiveData<List<FileModel>> filesLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> titleLiveData = new MutableLiveData<>();

    private String currentPath = "/";
    private int pendingScrollPosition = -1;
    private boolean isAppFirstLaunch = true;
    private boolean shouldSkipNextResume = false;
    private int requestGeneration;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MainViewModel(@NonNull Application application) {
        super(application);
    }

    public boolean isAppFirstLaunch() { return isAppFirstLaunch; }
    public void setAppLaunched() { isAppFirstLaunch = false; }

    /**
     * 是否应该跳过此次 onResume 的 reloadCurrent
     * （调用 loadRoot 后第一次 onResume 需要跳过，避免重复请求）
     */
    public boolean shouldSkipResume() {
        if (shouldSkipNextResume) {
            shouldSkipNextResume = false;
            return true;
        }
        return false;
    }

    public MutableLiveData<List<FileModel>> getFiles() { return filesLiveData; }
    public MutableLiveData<String> getError() { return errorLiveData; }
    public MutableLiveData<Boolean> getLoading() { return loadingLiveData; }
    public MutableLiveData<String> getTitle() { return titleLiveData; }

    public String getCurrentPath() { return currentPath; }

    public int getPendingScrollPosition() { return pendingScrollPosition; }
    public void setPendingScrollPosition(int pos) { this.pendingScrollPosition = pos; }
    public void clearPendingScroll() { this.pendingScrollPosition = -1; }
    public boolean hasPendingScroll() { return pendingScrollPosition >= 0; }

    /**
     * 加载根目录（首页）
     */
    public void loadRoot() {
        currentPath = "/";
        titleLiveData.setValue("儿童视频");
        shouldSkipNextResume = true;
        loadFiles("/");
    }

    /**
     * 进入子目录
     */
    public void navigateTo(String folderName) {
        currentPath = currentPath + folderName + "/";
        titleLiveData.setValue(folderName);
        loadFiles(currentPath);
    }

    /**
     * 返回上级目录
     */
    public boolean navigateUp() {
        if ("/".equals(currentPath)) {
            return false; // 已是根目录
        }
        // 去掉最后一个目录段
        // 格式: /folder1/folder2/ -> /folder1/
        String trimmed = currentPath.endsWith("/") ? currentPath.substring(0, currentPath.length() - 1) : currentPath;
        int lastSlash = trimmed.lastIndexOf('/');
        currentPath = trimmed.substring(0, lastSlash + 1);

        if ("/".equals(currentPath)) {
            titleLiveData.setValue("儿童视频");
        } else {
            // 提取当前目录名
            String parent = currentPath.substring(0, currentPath.length() - 1);
            String dirName = parent.substring(parent.lastIndexOf('/') + 1);
            titleLiveData.setValue(dirName);
        }
        loadFiles(currentPath);
        return true;
    }

    public boolean isRoot() {
        return "/".equals(currentPath);
    }

    /**
     * 重新加载当前目录（用于 onResume 刷新）
     */
    public void reloadCurrent() {
        if ("/".equals(currentPath)) {
            loadFiles("/");
        } else {
            loadFiles(currentPath);
        }
    }

    private void loadFiles(String path) {
        final int generation = ++requestGeneration;
        loadingLiveData.setValue(true);
        errorLiveData.setValue(null);
        filesLiveData.setValue(null);
        fileRepository.loadFiles(path, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String json) {
                try {
                    List<FileModel> files = new Gson().fromJson(json, new TypeToken<List<FileModel>>() {}.getType());
                    if (files == null) throw new IllegalArgumentException("目录数据为空");
                    for (FileModel file : files) {
                        if (file == null || file.name == null || file.name.trim().isEmpty()
                                || file.type < 0 || file.type > 2
                                || (file.type != 0 && (file.url == null || file.url.trim().isEmpty()))) {
                            throw new IllegalArgumentException("目录条目无效");
                        }
                    }
                    mainHandler.post(() -> {
                        if (generation != requestGeneration) return;
                        filesLiveData.setValue(files);
                        loadingLiveData.setValue(false);
                    });
                } catch (RuntimeException e) {
                    onError(new IOException("目录数据格式错误", e));
                }
            }

            @Override
            public void onError(IOException e) {
                mainHandler.post(() -> {
                    if (generation != requestGeneration) return;
                    errorLiveData.setValue("加载失败: " + e.getMessage());
                    loadingLiveData.setValue(false);
                });
            }
        });
    }
}
