package com.lm.android.tv.player.data.model;

import java.io.Serializable;

/**
 * 文件/目录数据模型
 */
public class FileModel implements Serializable {
    /** 文件/文件夹名称 */
    public String name;
    /** 封面图片相对路径 */
    public String cover;
    /** 类型：0=目录，1=视频，2=图片 */
    public int type;
    /** 当 type=1 或 type=2 时为文件相对路径 */
    public String url;
    /** 视频对应的外挂字幕文件名，可为空 */
    public String subtitle;

    public boolean isDirectory() { return type == 0; }
    public boolean isVideo() { return type == 1; }
    public boolean isImage() { return type == 2; }
}
