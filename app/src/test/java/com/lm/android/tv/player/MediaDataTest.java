package com.lm.android.tv.player;

import static org.junit.Assert.*;

import com.lm.android.tv.player.data.model.FileModel;
import com.lm.android.tv.player.data.repository.FileRepository;
import com.lm.android.tv.player.util.SubtitleUtils;
import com.lm.android.tv.player.util.Urls;

import org.junit.Test;

import java.util.List;

public class MediaDataTest {
    @Test
    public void buildsAndEncodesMediaUrls() {
        assertEquals(Urls.serverUrl + "/files.json", Urls.buildFilesUrl(null));
        assertEquals(Urls.serverUrl + "/动画/files.json", Urls.buildFilesUrl("/动画/"));
        assertEquals(Urls.serverUrl + "/动画/files.json", Urls.buildFilesUrl("/动画"));
        assertEquals(Urls.serverUrl + "/动画/一集.mp4", Urls.buildFileUrl("/动画", "一集.mp4"));
        assertEquals("http://example.com/%E5%8A%A8%E7%94%BB/%E4%B8%80.mp4",
                Urls.encodeChineseUrl("http://example.com/动画/一.mp4"));
        assertEquals("http://example.com/a%2Bb%20c.mp4",
                Urls.encodeChineseUrl("http://example.com/a+b c.mp4"));
        assertEquals(Urls.serverUrl + "/%E5%8A%A8%E7%94%BB/a%3Fb%23c.mp4",
                Urls.buildEncodedFileUrl("/动画/", "a?b#c.mp4"));
    }

    @Test
    public void parsesDirectoryVideoAndImageEntries() {
        String json = "[{\"name\":\"动画\",\"type\":0},"
                + "{\"name\":\"一集\",\"type\":1,\"url\":\"一集.mp4\",\"subtitle\":[\"一集-中文字幕.srt\",\"一集-英文字幕.srt\"]},"
                + "{\"name\":\"二集\",\"type\":1,\"url\":\"二集.mp4\",\"subtitle\":\"二集.srt\"},"
                + "{\"name\":\"封面\",\"type\":2,\"url\":\"封面.jpg\"}]";
        List<FileModel> files = FileRepository.parseFiles(json);
        assertEquals(4, files.size());
        assertTrue(files.get(0).isDirectory());
        assertTrue(files.get(1).isVideo());
        assertEquals("一集.mp4", files.get(1).url);
        assertArrayEquals(new String[]{"一集-中文字幕.srt", "一集-英文字幕.srt"}, files.get(1).subtitle);
        assertTrue(files.get(1).hasSubtitles());

        // 兼容单字符串格式
        assertArrayEquals(new String[]{"二集.srt"}, files.get(2).subtitle);

        assertNull(files.get(0).subtitle);
        assertFalse(files.get(0).hasSubtitles());
        assertNull(files.get(3).subtitle);
        assertTrue(files.get(3).isImage());
    }

    @Test
    public void distinguishesAnEmptyDirectoryFromMissingDataOrType() {
        assertTrue(FileRepository.parseFiles("[]").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> FileRepository.parseFiles(""));
        assertThrows(IllegalArgumentException.class,
                () -> FileRepository.parseFiles("[{\"name\":\"动画\"}]"));
        assertThrows(IllegalArgumentException.class,
                () -> FileRepository.parseFiles("[{\"name\":\"一集\",\"type\":1}]"));
        assertThrows(IllegalArgumentException.class,
                () -> FileRepository.parseFiles("[{\"name\":\"一集\",\"type\":1,\"url\":\"一集.mp4\",\"subtitle\":[\"../hack.srt\"]}]"));
    }

    @Test
    public void extractsSubtitleDisplayNames() {
        assertEquals("-中文字幕", SubtitleUtils.extractSubtitleDisplayName("aaa.mp4", "aaa-中文字幕.srt"));
        assertEquals("-英文字幕", SubtitleUtils.extractSubtitleDisplayName("aaa.mp4", "aaa-英文字幕.srt"));
        assertEquals("-双语", SubtitleUtils.extractSubtitleDisplayName("/path/to/aaa.mp4", "aaa-双语.vtt"));
        assertEquals("aaa", SubtitleUtils.extractSubtitleDisplayName("aaa.mp4", "aaa.srt"));
        assertEquals("other", SubtitleUtils.extractSubtitleDisplayName("aaa.mp4", "other.ass"));
        assertEquals("", SubtitleUtils.extractSubtitleDisplayName("aaa.mp4", null));
        assertEquals("", SubtitleUtils.extractSubtitleDisplayName("aaa.mp4", ""));
    }
}
