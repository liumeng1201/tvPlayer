package com.lm.android.tv.player.ui.setting;

import android.app.Application;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.lm.android.tv.player.data.model.UpdateModel;
import com.lm.android.tv.player.data.repository.UpdateRepository;
import com.lm.android.tv.player.util.SharedPrefsHelper;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 设置页面 ViewModel
 */
public class SettingViewModel extends AndroidViewModel {

    private final UpdateRepository updateRepository = new UpdateRepository();
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private Future<?> updateCheck;
    private final MutableLiveData<String[]> settingsLiveData = new MutableLiveData<>();
    private final MutableLiveData<UpdateModel> updateLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> toastLiveData = new MutableLiveData<>();

    public SettingViewModel(@NonNull Application application) {
        super(application);
        refreshSettings();
    }

    public MutableLiveData<String[]> getSettings() { return settingsLiveData; }
    public MutableLiveData<UpdateModel> getUpdateInfo() { return updateLiveData; }
    public MutableLiveData<String> getToast() { return toastLiveData; }

    public void refreshSettings() {
        int playMode = SharedPrefsHelper.getInt(getApplication(), SharedPrefsHelper.KEY_PLAY_MODE, 0);

        String[] settings = new String[2];
        settings[0] = "播放方式  " + (playMode == 0 ? "列表循环" : "单集循环");
        settings[1] = "检查更新    当前版本：" + getVersionName();
        settingsLiveData.setValue(settings);
    }

    public void setPlayMode(int value) {
        SharedPrefsHelper.putInt(getApplication(), SharedPrefsHelper.KEY_PLAY_MODE, value);
        refreshSettings();
    }

    private String getVersionName() {
        try {
            return getApplication().getPackageManager()
                    .getPackageInfo(getApplication().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }

    public void checkUpdate() {
        if (updateCheck != null && !updateCheck.isDone()) return;
        updateCheck = updateExecutor.submit(() -> {
            try {
                UpdateModel update = updateRepository.checkUpdate();
                if (update == null) {
                    toastLiveData.postValue("暂未发布可用版本");
                } else {
                    updateLiveData.postValue(update);
                }
            } catch (IOException e) {
                toastLiveData.postValue("无法获取 GitHub 更新，请稍后重试");
            }
        });
    }

    @Override
    protected void onCleared() {
        updateExecutor.shutdownNow();
        super.onCleared();
    }
}
