package com.lm.android.tv.player.ui.player;

import androidx.lifecycle.ViewModel;
import com.lm.android.tv.player.data.model.FileModel;
import java.util.ArrayList;
import java.util.List;

/** 在同一 Activity 内共享播放列表及其在完整目录中的位置。 */
public class PlaybackQueueViewModel extends ViewModel {
    private final ArrayList<FileModel> videos = new ArrayList<>();
    private final ArrayList<Integer> gridPositions = new ArrayList<>();

    public void setFiles(List<FileModel> files) {
        videos.clear();
        gridPositions.clear();
        if (files == null) return;
        for (int i = 0; i < files.size(); i++) {
            FileModel file = files.get(i);
            if (file != null && file.isVideo() && file.url != null && !file.url.isEmpty()) {
                videos.add(file);
                gridPositions.add(i);
            }
        }
    }

    /** 用于直接提供纯视频列表的调用方，目录位置即传入列表的位置。 */
    public void setVideos(List<FileModel> items) { setFiles(items); }
    public ArrayList<FileModel> getVideos() { return new ArrayList<>(videos); }
    public ArrayList<Integer> getGridPositions() { return new ArrayList<>(gridPositions); }
}
