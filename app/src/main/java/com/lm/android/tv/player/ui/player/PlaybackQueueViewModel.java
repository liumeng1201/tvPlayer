package com.lm.android.tv.player.ui.player;

import androidx.lifecycle.ViewModel;
import com.lm.android.tv.player.data.model.FileModel;
import java.util.ArrayList;
import java.util.List;

/** 在同一 Activity 内共享播放列表，避免把整个目录写入状态 Bundle。 */
public class PlaybackQueueViewModel extends ViewModel {
    private ArrayList<FileModel> videos = new ArrayList<>();
    public void setVideos(List<FileModel> items) { videos = new ArrayList<>(items); }
    public ArrayList<FileModel> getVideos() { return new ArrayList<>(videos); }
}
