package com.lm.android.tv.player.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * URL 相关工具类
 */
public final class Urls {

    /** 局域网服务器地址（可根据实际环境修改） */
    public static String serverUrl = "http://192.168.1.248";

    private Urls() {}

    /**
     * 构建 files.json 请求地址
     */
    public static String buildFilesUrl(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return serverUrl + "/files.json";
        }
        return serverUrl + (path.endsWith("/") ? path : path + "/") + "files.json";
    }

    /**
     * 构建文件/封面完整 URL
     */
    public static String buildFileUrl(String path, String fileName) {
        if (path == null) { path = "/"; }
        if (!path.endsWith("/")) { path += "/"; }
        return serverUrl + path + fileName;
    }

    /**
     * 对包含中文的 URL 进行编码（保留协议、路径分隔符等特殊字符）
     */
    public static String encodeChineseUrl(String originalUrl) {
        try {
            return URLEncoder.encode(originalUrl, "UTF-8")
                    .replace("+", "%20")
                    .replace("%3A", ":")
                    .replace("%2F", "/")
                    .replace("%3F", "?")
                    .replace("%3D", "=")
                    .replace("%26", "&");
        } catch (UnsupportedEncodingException e) {
            return originalUrl;
        }
    }
}
