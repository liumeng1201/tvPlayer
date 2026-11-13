package com.lm.android.tv.player.ui.main;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lm.android.tv.player.R;
import com.lm.android.tv.player.data.model.FileModel;
import com.lm.android.tv.player.ui.image.ImageFragment;
import com.lm.android.tv.player.ui.player.PlayerFragment;
import com.lm.android.tv.player.ui.player.PlaybackQueueViewModel;
import com.lm.android.tv.player.ui.setting.SettingFragment;
import com.lm.android.tv.player.ui.widget.FileGridAdapter;
import com.lm.android.tv.player.ui.widget.GridItemDecoration;
import com.lm.android.tv.player.util.ParentalControlManager;
import com.lm.android.tv.player.util.SharedPrefsHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 主界面 —— 宫格文件浏览
 */
public class MainFragment extends Fragment {

    private static final String TAG = "MainFragment";
    private static final int GRID_COLUMNS = 4;
    private static final int GRID_SPACING_DP = 12;

    private MainViewModel viewModel;
    private FileGridAdapter adapter;
    private RecyclerView recyclerView;
    private TextView tvTitle;
    private TextView tvError;
    private Button btnSetting;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        tvTitle = view.findViewById(R.id.tv_title);
        tvError = view.findViewById(R.id.tv_error);
        btnSetting = view.findViewById(R.id.btn_setting);
        recyclerView = view.findViewById(R.id.rv_grid);

        setupRecyclerView();
        setupSettingsButton();
        observeViewModel();

        if (viewModel.isAppFirstLaunch()) {
            viewModel.loadRoot();
            viewModel.setAppLaunched();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从其他 Fragment（设置页、播放器、图片）返回时刷新数据
        // 但不包括紧跟在 loadRoot 之后的首次 onResume（避免重复请求）
        if (!viewModel.shouldSkipResume()) {
            viewModel.reloadCurrent();
        }
    }

    private void setupRecyclerView() {
        adapter = new FileGridAdapter();
        adapter.setOnItemClickListener((position, item) -> onItemClicked(item));

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), GRID_COLUMNS));
        int spacing = (int) (GRID_SPACING_DP * getResources().getDisplayMetrics().density);
        recyclerView.addItemDecoration(new GridItemDecoration(spacing));

        recyclerView.setHasFixedSize(true);
        recyclerView.setFocusable(false);
        recyclerView.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);

        recyclerView.setAdapter(adapter);
    }

    private void setupSettingsButton() {
        btnSetting.setOnClickListener(v -> {
            if (getActivity() == null) return;
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new SettingFragment())
                    .addToBackStack(null)
                    .commit();
        });

        btnSetting.setFocusable(true);
        btnSetting.setFocusableInTouchMode(true);
    }

    private void observeViewModel() {
        viewModel.getTitle().observe(getViewLifecycleOwner(), title ->
                tvTitle.setText(title));

        viewModel.getFiles().observe(getViewLifecycleOwner(), files -> {
            adapter.setCurrentPath(viewModel.getCurrentPath());
            adapter.setItems(files);
            if (files != null && files.isEmpty()) {
                tvError.setText("目录为空");
                tvError.setVisibility(View.VISIBLE);
            }
            if (files != null && !files.isEmpty()) {
                tvError.setVisibility(View.GONE);
                // 目录/返回导航后，数据加载完成，执行待滚动
                if (viewModel.hasPendingScroll()) {
                    int pos = viewModel.getPendingScrollPosition();
                    recyclerView.scrollToPosition(pos);
                    recyclerView.post(() -> {
                        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(pos);
                        if (holder != null) holder.itemView.requestFocus();
                    });
                    viewModel.clearPendingScroll();
                }
            }
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (loading != null && loading) {
                tvError.setText("加载中...");
                tvError.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Log.e(TAG, error);
                tvError.setText(error);
                tvError.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });

        // 设置按钮仅根目录可见
        viewModel.getTitle().observe(getViewLifecycleOwner(), title ->
                btnSetting.setVisibility(viewModel.isRoot() ? View.VISIBLE : View.GONE));
    }

    private void onItemClicked(FileModel item) {
        if (item.isDirectory()) {
            // 保存当前目录的滚动位置
            SharedPrefsHelper.putLastPosition(requireContext(),
                    viewModel.getCurrentPath(), getCurrentScrollPosition());

            // 获取子目录上次保存的位置，设为待滚动目标
            String childPath = viewModel.getCurrentPath() + item.name + "/";
            int lastPos = SharedPrefsHelper.getLastPosition(requireContext(), childPath);
            viewModel.setPendingScrollPosition(lastPos);

            // 进入子目录（异步加载完成后再执行滚动）
            viewModel.navigateTo(item.name);

        } else if (item.isVideo()) {
            openPlayer(item);

        } else if (item.isImage()) {
            openImage(item);
        }
    }

    private void openPlayer(FileModel item) {
        // 检查冷却期，避免无谓创建 PlayerFragment
        if (ParentalControlManager.isInCooldown(requireContext())) {
            String remaining = ParentalControlManager.getCooldownRemainingText(requireContext());
            Toast.makeText(getContext(), "休息时间还没结束，请" + remaining + "之后再回来~", Toast.LENGTH_LONG).show();
            return;
        }

        List<FileModel> files = viewModel.getFiles().getValue();
        List<FileModel> videoList = new ArrayList<>();
        int targetIndex = 0;

        if (files != null) {
            for (int i = 0; i < files.size(); i++) {
                FileModel f = files.get(i);
                if (f.isVideo() && f.url != null && !f.url.isEmpty()) {
                    if (f.url.equals(item.url)) {
                        targetIndex = videoList.size();
                    }
                    videoList.add(f);
                }
            }
        }
        if (videoList.isEmpty()) {
            videoList.add(item);
        }

        Bundle args = new Bundle();
        args.putString(PlayerFragment.ARG_PATH, viewModel.getCurrentPath());
        args.putInt(PlayerFragment.ARG_INDEX, targetIndex);
        new ViewModelProvider(requireActivity()).get(PlaybackQueueViewModel.class).setVideos(videoList);

        PlayerFragment fragment = new PlayerFragment();
        fragment.setArguments(args);

        if (getActivity() != null) {
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    private void openImage(FileModel item) {
        Bundle args = new Bundle();
        args.putString(ImageFragment.ARG_PATH, viewModel.getCurrentPath());
        args.putString(ImageFragment.ARG_URL, item.url);
        args.putString(ImageFragment.ARG_NAME, item.name);

        ImageFragment fragment = new ImageFragment();
        fragment.setArguments(args);

        if (getActivity() != null) {
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    private int getCurrentScrollPosition() {
        GridLayoutManager lm = (GridLayoutManager) recyclerView.getLayoutManager();
        return lm != null ? lm.findFirstVisibleItemPosition() : 0;
    }

    public boolean onBackPressed() {
        if (viewModel.navigateUp()) {
            // 设置待滚动位置：恢复上级目录之前保存的位置
            int lastPos = SharedPrefsHelper.getLastPosition(requireContext(), viewModel.getCurrentPath());
            viewModel.setPendingScrollPosition(lastPos);
            return true;
        }
        return false;
    }
}
