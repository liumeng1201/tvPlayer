package com.lm.android.tv.player;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class Urls {
    public static final String serverUrl = "http://192.168.1.248";

    public static String encodeChineseUrl(String originalUrl) {
        try {
            // 对整个URL进行编码
            // 注意：使用UTF-8编码，这是标准的URL编码方式
            return URLEncoder.encode(originalUrl, "UTF-8")
                    // 恢复一些不需要编码的特殊字符
                    .replace("%3A", ":")
                    .replace("%2F", "/")
                    .replace("%3F", "?")
                    .replace("%3D", "=")
                    .replace("%26", "&");
        } catch (UnsupportedEncodingException e) {
            // UTF-8编码是所有Android系统都支持的，所以这里异常理论上不会发生
            e.printStackTrace();
            return originalUrl;
        }
    }
}
