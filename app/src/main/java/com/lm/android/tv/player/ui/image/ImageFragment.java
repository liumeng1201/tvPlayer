package com.lm.android.tv.player.ui.image;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.lm.android.tv.player.R;
import com.lm.android.tv.player.util.Urls;

/**
 * 图片查看页面
 */
public class ImageFragment extends Fragment {

    public static final String ARG_PATH = "path";
    public static final String ARG_URL = "url";
    public static final String ARG_NAME = "name";

    private String path;
    private String url;
    private String name;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            path = args.getString(ARG_PATH, "/");
            url = args.getString(ARG_URL, "");
            name = args.getString(ARG_NAME, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_image, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView imageView = view.findViewById(R.id.iv_image);
        TextView tvTitle = view.findViewById(R.id.tv_image_title);

        tvTitle.setText(name);

        String fullUrl = Urls.encodeChineseUrl(Urls.buildFileUrl(path, url));
        Glide.with(this)
                .load(fullUrl)
                .centerInside()
                .into(imageView);
    }
}
