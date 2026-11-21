package com.lm.android.tv.player.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 字幕相关工具方法
 */
public final class SubtitleUtils {

    private SubtitleUtils() {}

    /**
     * 提取字幕在界面上展示的名称：
     * 移除视频文件名部分（不含扩展名）以及字幕文件本身的扩展名。
     * 例如视频为 aaa.mp4，字幕为 aaa-中文字幕.srt，展示结果为 -中文字幕。
     *
     * @param videoNameOrUrl    视频文件名或路径（如 aaa.mp4 或 /path/to/aaa.mp4）
     * @param subtitleFileName  字幕文件名或路径（如 aaa-中文字幕.srt）
     * @return 裁剪后的展示名称
     */
    @NonNull
    public static String extractSubtitleDisplayName(@Nullable String videoNameOrUrl, @Nullable String subtitleFileName) {
        if (subtitleFileName == null || subtitleFileName.trim().isEmpty()) {
            return "";
        }

        String subBaseName = stripExtension(getFileName(subtitleFileName));
        String videoBaseName = videoNameOrUrl != null ? stripExtension(getFileName(videoNameOrUrl)) : "";

        if (!videoBaseName.isEmpty()) {
            if (subBaseName.toLowerCase().startsWith(videoBaseName.toLowerCase())) {
                String trimmed = subBaseName.substring(videoBaseName.length());
                if (!trimmed.trim().isEmpty()) {
                    return trimmed;
                }
            }
        }

        return subBaseName.isEmpty() ? subtitleFileName : subBaseName;
    }

    @NonNull
    private static String getFileName(@NonNull String path) {
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash < path.length() - 1) {
            return path.substring(slash + 1);
        }
        return path;
    }

    @NonNull
    private static String stripExtension(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            return fileName.substring(0, dot);
        }
        return fileName;
    }
}
