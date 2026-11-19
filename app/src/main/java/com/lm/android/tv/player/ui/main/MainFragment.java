package com.lm.android.tv.player.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.OneShotPreDrawListener;
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

public class MainFragment extends Fragment {
    private static final int GRID_COLUMNS = 4;
    private static final int GRID_SPACING_DP = 12;

    private MainViewModel viewModel;
    private FileGridAdapter adapter;
    private RecyclerView recyclerView;
    private TextView tvTitle;
    private TextView tvError;
    private Button btnSetting;
    private Button retry;
    private OneShotPreDrawListener focusRestorer;
    private boolean openingPage;
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusListener = (oldFocus, newFocus) -> {
        if (focusRestorer == null && !openingPage) saveFocusedItem();
    };

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
        retry = view.findViewById(R.id.btn_retry_directory);
        recyclerView = view.findViewById(R.id.rv_grid);

        adapter = new FileGridAdapter();
        adapter.setOnItemClickListener(this::onItemClicked);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), GRID_COLUMNS));
        int spacing = (int) (GRID_SPACING_DP * getResources().getDisplayMetrics().density);
        recyclerView.addItemDecoration(new GridItemDecoration(spacing));
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);
        recyclerView.getViewTreeObserver().addOnGlobalFocusChangeListener(focusListener);

        btnSetting.setOnClickListener(v -> openPage(new SettingFragment()));
        retry.setOnClickListener(v -> viewModel.reloadCurrent());
        viewModel.getDirectory().observe(getViewLifecycleOwner(), this::render);
        viewModel.ensureLoaded();
    }

    @Override
    public void onResume() {
        super.onResume();
        openingPage = false;
    }

    private void render(MainViewModel.DirectoryState state) {
        if (focusRestorer != null) {
            focusRestorer.removeListener();
            focusRestorer = null;
        }
        tvTitle.setText(state.title);
        btnSetting.setVisibility(viewModel.isRoot() ? View.VISIBLE : View.GONE);
        adapter.setCurrentPath(state.path);
        adapter.setItems(state.files);
        retry.setVisibility(state.error == null ? View.GONE : View.VISIBLE);
        if (state.error != null) {
            showStatus(state.error);
            retry.requestFocus();
        } else if (state.files == null) {
            showStatus(getString(R.string.directory_loading));
        } else if (state.files.isEmpty()) {
            showStatus(getString(R.string.directory_empty));
        } else {
            tvError.setVisibility(View.GONE);
            restoreFocus(Math.min(viewModel.getFocusPosition(), state.files.size() - 1));
        }
    }

    private void showStatus(String status) {
        tvError.setText(status);
        tvError.setVisibility(View.VISIBLE);
    }

    private void restoreFocus(int position) {
        int target = Math.max(0, position);
        recyclerView.scrollToPosition(target);
        focusRestorer = OneShotPreDrawListener.add(recyclerView, () -> {
            focusRestorer = null;
            RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(target);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    private void onItemClicked(int position, FileModel item) {
        if (openingPage || adapter.getItem(position) != item) return;
        viewModel.saveFocusPosition(position);
        if (item.isDirectory()) {
            viewModel.navigateTo(item.name);
        } else if (item.isVideo()) {
            openPlayer(position);
        } else if (item.isImage()) {
            Bundle args = new Bundle();
            args.putString(ImageFragment.ARG_PATH, viewModel.getCurrentPath());
            args.putString(ImageFragment.ARG_URL, item.url);
            args.putString(ImageFragment.ARG_NAME, item.name);
            ImageFragment image = new ImageFragment();
            image.setArguments(args);
            openPage(image);
        }
    }

    private void openPlayer(int position) {
        if (ParentalControlManager.isInCooldown(requireContext())) {
            String remaining = ParentalControlManager.getCooldownRemainingText(requireContext());
            Toast.makeText(getContext(), "休息时间还没结束，请" + remaining + "之后再回来~", Toast.LENGTH_LONG).show();
            return;
        }
        PlaybackQueueViewModel queue = new ViewModelProvider(requireActivity())
                .get(PlaybackQueueViewModel.class);
        queue.setFiles(viewModel.getDirectory().getValue().files);
        int videoIndex = queue.getGridPositions().indexOf(position);
        if (videoIndex < 0) return;

        Bundle args = new Bundle();
        args.putString(PlayerFragment.ARG_PATH, viewModel.getCurrentPath());
        args.putInt(PlayerFragment.ARG_INDEX, videoIndex);
        PlayerFragment player = new PlayerFragment();
        player.setArguments(args);
        openPage(player);
    }

    private void openPage(Fragment page) {
        if (openingPage || getParentFragmentManager().isStateSaved()) return;
        openingPage = true;
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, page)
                .addToBackStack(null)
                .commit();
    }

    private void saveFocusedItem() {
        if (recyclerView == null) return;
        View focused = recyclerView.getFocusedChild();
        if (focused != null) {
            viewModel.saveFocusPosition(recyclerView.getChildAdapterPosition(focused));
        }
    }

    @Override
    public void onPause() {
        if (!openingPage) saveFocusedItem();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (focusRestorer != null) {
            focusRestorer.removeListener();
            focusRestorer = null;
        }
        recyclerView.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);
        recyclerView.setAdapter(null);
        recyclerView = null;
        adapter = null;
        tvTitle = null;
        tvError = null;
        btnSetting = null;
        retry = null;
        super.onDestroyView();
    }

    public boolean onBackPressed() {
        saveFocusedItem();
        return viewModel.navigateUp();
    }
}
