package com.lm.android.tv.player;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lm.android.tv.player.data.model.FileModel;
import com.lm.android.tv.player.util.Urls;

import org.junit.Test;

import java.util.List;

public class MediaDataTest {
    @Test
    public void buildsAndEncodesMediaUrls() {
        assertEquals(Urls.serverUrl + "/files.json", Urls.buildFilesUrl(null));
        assertEquals(Urls.serverUrl + "/动画/files.json", Urls.buildFilesUrl("/动画/"));
        assertEquals(Urls.serverUrl + "/动画/一集.mp4", Urls.buildFileUrl("/动画", "一集.mp4"));
        assertEquals("http://example.com/%E5%8A%A8%E7%94%BB/%E4%B8%80.mp4",
                Urls.encodeChineseUrl("http://example.com/动画/一.mp4"));
    }

    @Test
    public void parsesDirectoryVideoAndImageEntries() {
        String json = "[{\"name\":\"动画\",\"type\":0},"
                + "{\"name\":\"一集\",\"type\":1,\"url\":\"一集.mp4\",\"subtitle\":\"一集.srt\"},"
                + "{\"name\":\"封面\",\"type\":2,\"url\":\"封面.jpg\"}]";
        List<FileModel> files = new Gson().fromJson(json, new TypeToken<List<FileModel>>() {}.getType());
        assertEquals(3, files.size());
        assertTrue(files.get(0).isDirectory());
        assertTrue(files.get(1).isVideo());
        assertEquals("一集.mp4", files.get(1).url);
        assertEquals("一集.srt", files.get(1).subtitle);
        assertNull(files.get(0).subtitle);
        assertNull(files.get(2).subtitle);
        assertTrue(files.get(2).isImage());
    }
}
