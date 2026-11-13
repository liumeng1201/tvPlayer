package com.lm.android.tv.player.ui.setting;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lm.android.tv.player.R;
import com.lm.android.tv.player.data.model.UpdateModel;
import com.lm.android.tv.player.util.SharedPrefsHelper;

/**
 * 设置页面
 */
public class SettingFragment extends Fragment {

    private SettingViewModel viewModel;
    private SettingsAdapter adapter;
    private BroadcastReceiver downloadReceiver;
    private Context receiverContext;
    private long activeDownloadId = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_setting, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(SettingViewModel.class);

        RecyclerView recyclerView = view.findViewById(R.id.rv_settings);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new SettingsAdapter();
        adapter.setOnItemClickListener(this::onSettingClicked);
        recyclerView.setAdapter(adapter);

        viewModel.getSettings().observe(getViewLifecycleOwner(), settings ->
                adapter.setItems(settings));

        viewModel.getUpdateInfo().observe(getViewLifecycleOwner(), update -> {
            if (update != null) {
                showUpdateDialog(update);
            }
        });

        viewModel.getToast().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) {
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
        restorePendingDownload();
    }

    private void onSettingClicked(int position) {
        switch (position) {
            case 0:
                showPlayModeDialog();
                break;
            case 1:
                viewModel.checkUpdate();
                break;
        }
    }

    private void showPlayModeDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle("请选择")
                .setItems(new String[]{"列表循环", "单集循环"},
                        (dialog, which) -> viewModel.setPlayMode(which))
                .create()
                .show();
    }

    private void showUpdateDialog(UpdateModel update) {
        if (update.versionCode <= getVersionCode()) {
            Toast.makeText(getContext(), "已是最新版本", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(getContext())
                .setTitle("检查更新")
                .setMessage("当前版本：" + getVersionName() +
                        "\n检测到新版本：" + update.versionName + "\n是否升级？")
                .setPositiveButton("升级", (dialog, which) ->
                        downloadApk(update.downloadUrl))
                .create()
                .show();
    }

    private void downloadApk(String url) {
        if (getContext() == null) return;

        Toast.makeText(getContext(), "开始下载...", Toast.LENGTH_SHORT).show();
        Context appContext = requireContext().getApplicationContext();

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("应用更新下载");
        request.setDescription("正在下载新版本...");
        request.setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS,
                "app-update-" + System.currentTimeMillis() + ".apk");
        request.setMimeType("application/vnd.android.package-archive");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        DownloadManager dm = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
        unregisterDownloadReceiver();
        registerDownloadReceiver(appContext, dm);
        try {
            activeDownloadId = dm.enqueue(request);
            SharedPrefsHelper.putLong(appContext, SharedPrefsHelper.KEY_UPDATE_DOWNLOAD_ID, activeDownloadId);
        } catch (RuntimeException e) {
            unregisterDownloadReceiver();
            SharedPrefsHelper.putLong(appContext, SharedPrefsHelper.KEY_UPDATE_DOWNLOAD_ID, -1);
            Toast.makeText(appContext, "无法开始更新下载", Toast.LENGTH_SHORT).show();
        }
    }

    private void restorePendingDownload() {
        Context appContext = requireContext().getApplicationContext();
        long id = SharedPrefsHelper.getLong(appContext, SharedPrefsHelper.KEY_UPDATE_DOWNLOAD_ID, -1);
        if (id < 0) return;
        DownloadManager dm = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
        activeDownloadId = id;
        registerDownloadReceiver(appContext, dm);
        handleDownloadCompletion(appContext, dm, id);
    }

    private void registerDownloadReceiver(Context context, DownloadManager dm) {
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == activeDownloadId && id != -1) {
                    handleDownloadCompletion(receiverContext, dm, id);
                }
            }
        };
        ContextCompat.registerReceiver(context, downloadReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED);
        receiverContext = context;
    }

    private void handleDownloadCompletion(Context context, DownloadManager dm, long id) {
        int status = DownloadManager.STATUS_FAILED;
        try (Cursor cursor = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor != null && cursor.moveToFirst()) {
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            }
        }
        if (status != DownloadManager.STATUS_SUCCESSFUL && status != DownloadManager.STATUS_FAILED) return;
        SharedPrefsHelper.putLong(context, SharedPrefsHelper.KEY_UPDATE_DOWNLOAD_ID, -1);
        unregisterDownloadReceiver();
        if (status == DownloadManager.STATUS_SUCCESSFUL && isAdded()) installApk(context, id);
        else if (isAdded()) Toast.makeText(context, "更新下载失败", Toast.LENGTH_SHORT).show();
    }

    private void unregisterDownloadReceiver() {
        if (downloadReceiver != null && receiverContext != null) {
            receiverContext.unregisterReceiver(downloadReceiver);
        }
        downloadReceiver = null;
        receiverContext = null;
        activeDownloadId = -1;
    }

    @Override
    public void onDestroyView() {
        unregisterDownloadReceiver();
        super.onDestroyView();
    }

    private void installApk(Context context, long downloadId) {
        Uri uri = ((DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE))
                .getUriForDownloadedFile(downloadId);
        if (uri != null) {
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(uri, "application/vnd.android.package-archive");
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(installIntent);
        }
    }

    private int getVersionCode() {
        try {
            return requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private String getVersionName() {
        try {
            return requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ----- 设置项适配器 -----

    private static class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.ViewHolder> {

        private String[] items = new String[0];
        private OnItemClickListener listener;

        void setItems(String[] items) {
            this.items = items;
            notifyDataSetChanged();
        }

        void setOnItemClickListener(OnItemClickListener listener) {
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_setting, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.textView.setText(items[position]);
            holder.textView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(position);
            });
        }

        @Override
        public int getItemCount() {
            return items.length;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.tv_setting_item);
            }
        }

        interface OnItemClickListener {
            void onItemClick(int position);
        }
    }
}
