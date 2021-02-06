package com.lm.android.tv.player;

import androidx.annotation.NonNull;

import java.io.Serializable;

/**
 * 类listview中item点击事件监听
 * Created by liumeng on 2016/8/5.
 */
public interface CustomOnItemClickListener extends Serializable {
    void onItemClick(@NonNull int position);
}