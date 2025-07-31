package com.lm.android.tv.player.model;

import java.io.Serializable;

public class FileModel implements Serializable {
    public String name;
    public String cover;
    // 0-目录；1-视频；2-图片
    public int type;
    // 当 type=1 时为表示文件url
    public String url;
}
