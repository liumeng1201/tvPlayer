package com.lm.android.tv.player.ui.main;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.lm.android.tv.player.data.model.FileModel;
import com.lm.android.tv.player.data.repository.FileRepository;
import com.lm.android.tv.player.util.SharedPrefsHelper;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;

/** 管理当前目录、焦点位置和目录请求。 */
public class MainViewModel extends AndroidViewModel {
    private static final String PATH = "directory_path";

    private final SavedStateHandle savedState;
    private final FileRepository fileRepository = new FileRepository();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<DirectoryState> directory = new MutableLiveData<>();
    private Call request;
    private int requestGeneration;

    public MainViewModel(@NonNull Application application, SavedStateHandle savedState) {
        super(application);
        this.savedState = savedState;
    }

    public LiveData<DirectoryState> getDirectory() { return directory; }

    public String getCurrentPath() {
        String path = savedState.get(PATH);
        return path == null ? "/" : path;
    }

    public boolean isRoot() { return "/".equals(getCurrentPath()); }

    public int getFocusPosition() {
        return SharedPrefsHelper.getLastPosition(getApplication(), getCurrentPath());
    }

    public void saveFocusPosition(int position) {
        if (position >= 0) {
            SharedPrefsHelper.putLastPosition(getApplication(), getCurrentPath(), position);
        }
    }

    public void ensureLoaded() {
        if (directory.getValue() == null) reloadCurrent();
    }

    public void navigateTo(String folderName) {
        loadDirectory(getCurrentPath() + folderName + "/");
    }

    public boolean navigateUp() {
        String path = getCurrentPath();
        if ("/".equals(path)) return false;
        loadDirectory(path.substring(0, path.lastIndexOf('/', path.length() - 2) + 1));
        return true;
    }

    public void reloadCurrent() {
        loadDirectory(getCurrentPath());
    }

    private void loadDirectory(String path) {
        savedState.set(PATH, path);
        int generation = ++requestGeneration;
        if (request != null) request.cancel();

        String title = "/".equals(path) ? "儿童视频"
                : path.substring(path.lastIndexOf('/', path.length() - 2) + 1, path.length() - 1);
        directory.setValue(new DirectoryState(path, title, null, null));
        request = fileRepository.loadFiles(path, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(List<FileModel> files) {
                publish(generation, new DirectoryState(path, title, files, null));
            }

            @Override
            public void onError(IOException e) {
                publish(generation, new DirectoryState(path, title, null, "加载失败: " + e.getMessage()));
            }
        });
    }

    private void publish(int generation, DirectoryState state) {
        mainHandler.post(() -> {
            if (generation != requestGeneration) return;
            request = null;
            directory.setValue(state);
        });
    }

    @Override
    protected void onCleared() {
        requestGeneration++;
        if (request != null) request.cancel();
        mainHandler.removeCallbacksAndMessages(null);
        super.onCleared();
    }

    static final class DirectoryState {
        final String path;
        final String title;
        final List<FileModel> files;
        final String error;

        DirectoryState(String path, String title, List<FileModel> files, String error) {
            this.path = path;
            this.title = title;
            this.files = files;
            this.error = error;
        }
    }
}
