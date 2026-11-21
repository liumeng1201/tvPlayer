package com.lm.android.tv.player.data.model;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件/目录数据模型
 */
public class FileModel implements Serializable {
    /** 文件/文件夹名称 */
    public String name;
    /** 封面图片相对路径 */
    public String cover;
    /** 类型：0=目录，1=视频，2=图片 */
    public int type = -1;
    /** 当 type=1 或 type=2 时为文件相对路径 */
    public String url;
    /** 视频对应的外挂字幕文件名列表，可为空 */
    @JsonAdapter(StringArrayOrStringAdapter.class)
    public String[] subtitle;

    public boolean isDirectory() { return type == 0; }
    public boolean isVideo() { return type == 1; }
    public boolean isImage() { return type == 2; }

    public boolean hasSubtitles() {
        return subtitle != null && subtitle.length > 0;
    }

    public static class StringArrayOrStringAdapter extends TypeAdapter<String[]> {
        @Override
        public void write(JsonWriter out, String[] value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.beginArray();
            for (String s : value) {
                out.value(s);
            }
            out.endArray();
        }

        @Override
        public String[] read(JsonReader in) throws IOException {
            JsonToken token = in.peek();
            if (token == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            if (token == JsonToken.STRING) {
                String single = in.nextString();
                return new String[]{single};
            }
            if (token == JsonToken.BEGIN_ARRAY) {
                List<String> list = new ArrayList<>();
                in.beginArray();
                while (in.hasNext()) {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                    } else {
                        list.add(in.nextString());
                    }
                }
                in.endArray();
                return list.toArray(new String[0]);
            }
            in.skipValue();
            return null;
        }
    }
}
