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

/**
 * 设置页面 ViewModel
 */
public class SettingViewModel extends AndroidViewModel {

    private final UpdateRepository updateRepository = new UpdateRepository();
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
        updateRepository.checkUpdate(new UpdateRepository.UpdateCallback() {
            @Override
            public void onSuccess(String json) {
                try {
                    UpdateModel update = new com.google.gson.Gson().fromJson(json, UpdateModel.class);
                    updateLiveData.postValue(update);
                } catch (Exception e) {
                    toastLiveData.postValue("解析更新信息失败");
                }
            }

            @Override
            public void onError(IOException e) {
                toastLiveData.postValue("已是最新版本");
            }
        });
    }
}
