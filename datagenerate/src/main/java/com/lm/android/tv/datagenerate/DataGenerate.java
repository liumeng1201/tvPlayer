package com.lm.android.tv.datagenerate;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DataGenerate {

    private String[] ss = {
            "./datagenerate/src/main/asserts/s21.csv",
            "./datagenerate/src/main/asserts/s31.csv",
            "./datagenerate/src/main/asserts/s41.csv",
            "./datagenerate/src/main/asserts/s51.csv",
            "./datagenerate/src/main/asserts/s61.csv"
    };
    private String[] categoryNames = {
            "可爱巧虎岛 第2季",
            "可爱巧虎岛 第3季",
            "可爱巧虎岛 第4季",
            "可爱巧虎岛 第5季",
            "可爱巧虎岛 第6季"
    };

    public static List<String> readFile2List(File file) {
        int st = 0;
        int end = 2147483647;
        BufferedReader reader = null;
        try {
            int curLine = 1;
            List<String> list = new ArrayList();
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));

            String line;
            for (; (line = reader.readLine()) != null && curLine <= end; ++curLine) {
                if (st <= curLine && curLine <= end) {
                    list.add(line);
                }
            }

            return list;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean writeFileFromString(File file, String content, boolean append) {
        if (file != null && content != null) {
            BufferedWriter bw = null;
            try {
                bw = new BufferedWriter(new FileWriter(file, append));
                bw.write(content);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            } finally {
                try {
                    if (bw != null) {
                        bw.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            return false;
        }
    }

    private void generateJson() throws UnsupportedEncodingException {
        ArrayList<Category> categories = new ArrayList<>();

        for (int si = 0; si < ss.length; si++) {
            String s = ss[si];
            Category category = new Category();
            category.name = categoryNames[si];
            if (si == 6) {
                category.cover = "https://enable-ireading.oss-cn-shanghai.aliyuncs.com/cartoon/PeppaPig/peppapigen.webp";
            } else if (si == 7) {
                category.cover = "https://enable-ireading.oss-cn-shanghai.aliyuncs.com/cartoon/PeppaPig/jojo.jpg";
            } else {
                category.cover = "https://enable-ireading.oss-cn-shanghai.aliyuncs.com/cartoon/PeppaPig/%E5%B7%A7%E8%99%8E%E5%8F%AF%E7%88%B1%E5%B2%9B/qhd.jpg";
            }
            category.videos = new ArrayList<>();
            List<String> lines = readFile2List(new File(s));
            if (lines != null && lines.size() > 0) {
                for (int i = 0; i < lines.size() - 1; i = i + 2) {
                    Video video = new Video();
                    video.video = lines.get(i);
                    video.cover = lines.get(i + 1);

                    String[] strs = video.video.split("/");
                    String name = strs[strs.length - 1];
                    String videoname = URLDecoder.decode(name.substring(0, name.lastIndexOf(".mp4")), "UTF-8");
                    video.name = videoname;//name.substring(0, name.lastIndexOf(".mp4")).replaceAll("%20", " ");

                    category.videos.add(video);
                }
            }
            categories.add(category);
        }

        String json = new Gson().toJson(categories);
        writeFileFromString(new File("./datagenerate/src/main/asserts/cartoon.json"), json, false);
    }

    public static void main(String[] args) {
        try {
            new DataGenerate().generateJson();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
}