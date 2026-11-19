package com.lm.android.tv.player.ui.image;

import android.graphics.drawable.Drawable;
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
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
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
    private ImageView imageView;

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

        imageView = view.findViewById(R.id.iv_image);
        TextView tvTitle = view.findViewById(R.id.tv_image_title);
        tvTitle.setText(name);
        View retry = view.findViewById(R.id.btn_retry_image);
        retry.setOnClickListener(v -> loadImage(retry));
        loadImage(retry);
    }

    private void loadImage(View retry) {
        retry.setVisibility(View.GONE);
        String fullUrl = Urls.buildEncodedFileUrl(path, url);
        Glide.with(this)
                .load(fullUrl)
                .centerInside()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                Target<Drawable> target, boolean firstResource) {
                        retry.setVisibility(View.VISIBLE);
                        retry.requestFocus();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable drawable, Object model, Target<Drawable> target,
                                                   DataSource source, boolean firstResource) {
                        return false;
                    }
                })
                .into(imageView);
    }

    @Override
    public void onDestroyView() {
        Glide.with(this).clear(imageView);
        imageView = null;
        super.onDestroyView();
    }
}
