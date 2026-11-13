package com.lm.android.tv.player.ui.widget;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.lm.android.tv.player.R;
import com.lm.android.tv.player.data.model.FileModel;
import com.lm.android.tv.player.util.Urls;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件宫格适配器（4列）
 */
public class FileGridAdapter extends RecyclerView.Adapter<FileGridAdapter.ViewHolder> {

    private final List<FileModel> items = new ArrayList<>();
    private OnItemClickListener onItemClickListener;
    private String currentPath = "/";

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_grid_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileModel item = items.get(position);

        // 加载封面
        int fallback = item.isDirectory() ? R.drawable.ic_folder
                : item.isVideo() ? R.drawable.ic_video : R.drawable.item_bg;
        if (item.cover == null || item.cover.trim().isEmpty()) {
            Glide.with(holder.cover.getContext()).clear(holder.cover);
            holder.cover.setImageResource(fallback);
        } else {
            Glide.with(holder.cover.getContext())
                    .load(Urls.buildEncodedFileUrl(currentPath, item.cover))
                    .placeholder(fallback)
                    .error(fallback)
                    .centerCrop()
                    .into(holder.cover);
        }

        // 设置名称
        holder.title.setText(item.name);

        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(position, item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setItems(List<FileModel> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void setCurrentPath(String path) {
        this.currentPath = path;
    }

    public FileModel getItem(int position) {
        if (position >= 0 && position < items.size()) {
            return items.get(position);
        }
        return null;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public interface OnItemClickListener {
        void onItemClick(int position, FileModel item);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.iv_cover);
            title = itemView.findViewById(R.id.tv_title);
        }
    }
}
