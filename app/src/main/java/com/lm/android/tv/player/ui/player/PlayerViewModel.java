package com.lm.android.tv.player.ui.player;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

/**
 * 播放器 ViewModel
 */
public class PlayerViewModel extends ViewModel {

    private final MutableLiveData<String> videoTitle = new MutableLiveData<>();
    private final MutableLiveData<String> videoUrl = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentIndex = new MutableLiveData<>();
    private final MutableLiveData<Integer> totalCount = new MutableLiveData<>();
    private final MutableLiveData<Boolean> titleVisible = new MutableLiveData<>(false);

    private List<?> videoList;  // List<FileModel> 或其他

    public LiveData<String> getVideoTitle() { return videoTitle; }
    public LiveData<String> getVideoUrl() { return videoUrl; }
    public LiveData<Integer> getCurrentIndex() { return currentIndex; }
    public LiveData<Integer> getTotalCount() { return totalCount; }
    public LiveData<Boolean> getTitleVisible() { return titleVisible; }

    public void setVideoList(List<?> list) {
        this.videoList = list;
        totalCount.setValue(list != null ? list.size() : 0);
    }

    public List<?> getVideoList() {
        return videoList;
    }

    public void setCurrentIndex(int index) {
        currentIndex.setValue(index);
    }

    public void showTitle(boolean show) {
        titleVisible.setValue(show);
    }
}
