package com.lm.android.tv.datagenerate;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DataGenerate {

    private String[] ss = {
            "./datagenerate/src/main/asserts/s1.csv",
            "./datagenerate/src/main/asserts/s2.csv",
            "./datagenerate/src/main/asserts/s3.csv",
            "./datagenerate/src/main/asserts/s4.csv",
            "./datagenerate/src/main/asserts/s5.csv",
            "./datagenerate/src/main/asserts/s6.csv"
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

    private void generateJson() {
        ArrayList<Category> categories = new ArrayList<>();

        for (String s : ss) {
            Category category = new Category();
            category.name = "category_name";
            category.cover = "https://enable-ireading.oss-cn-shanghai.aliyuncs.com/cartoon/PeppaPig/peppapig.jpg";
            category.videos = new ArrayList<>();
            List<String> lines = readFile2List(new File(s));
            if (lines != null && lines.size() > 0) {
                for (int i = 0; i < lines.size() - 1; i = i + 2) {
                    Video video = new Video();
                    video.video = lines.get(i);
                    video.cover = lines.get(i + 1);

                    String[] strs = video.video.split("/");
                    String name = strs[strs.length - 1];
                    video.name = name.substring(0, name.lastIndexOf(".mp4")).replaceAll("%20", " ");

                    category.videos.add(video);
                }
            }
            categories.add(category);
        }

        String json = new Gson().toJson(categories);
        writeFileFromString(new File("./datagenerate/src/main/asserts/cartoon.json"), json, false);
    }

    public static void main(String[] args) {
        new DataGenerate().generateJson();
    }
}