package com.lm.android.tv.player.ui.player;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.ui.PlayerView;

import com.lm.android.tv.player.R;
import com.lm.android.tv.player.data.db.AppDatabase;
import com.lm.android.tv.player.data.db.VideoProgressDao;
import com.lm.android.tv.player.data.db.VideoProgressEntity;
import com.lm.android.tv.player.data.model.FileModel;
import com.lm.android.tv.player.data.repository.FileRepository;
import com.lm.android.tv.player.player.ExoPlayerManager;
import com.lm.android.tv.player.util.ParentalControlManager;
import com.lm.android.tv.player.util.SharedPrefsHelper;
import com.lm.android.tv.player.util.Urls;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.util.function.Consumer;

import okhttp3.Call;

/**
 * 视频播放页面 —— 全屏播放 + 遥控器控制
 */
public class PlayerFragment extends Fragment {

    public static final String ARG_PATH = "path";
    public static final String ARG_INDEX = "index";
    private static final String TAG = "PlayerFragment";
    private static final long RESUME_LOOKUP_TIMEOUT_MS = 5000;

    private PlayerView playerView;
    private TextView tvTitle;
    private TextView tvSeekIndicator;
    private TextView tvRestartHint;
    private View bottomProgressContainer;
    private ProgressBar progressBar;
    private TextView tvProgressCurrent;
    private TextView tvProgressTotal;
    private TextView retry;

    private String path;
    private ArrayList<FileModel> videoList = new ArrayList<>();
    private ArrayList<Integer> gridPositions = new ArrayList<>();
    private int currentIndex;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideTitleRunnable;
    private Runnable hideSeekRunnable;
    private Runnable progressUpdater;
    private Runnable restartHintRunnable;
    private Runnable saveProgressRunnable;
    private Runnable accumulatedTimeUpdater;
    private boolean titleShouldShow;
    private boolean isProgressVisible;
    private Player.Listener playerListener;
    private int playbackGeneration;
    private long playingSinceElapsed = -1;
    private boolean resumeAfterLifecyclePause;
    private boolean restorePaused;
    private String restoredVideoFile;
    private long restoredPositionMs = -1;
    private boolean pendingInitialPlayback;
    private boolean resumeLookupPending;
    private boolean playAfterResumeLookup;
    private Runnable resumeLookupTimeout;
    private Call queueRequest;
    private boolean queueLoadFailed;
    private String queueRestoreError;
    private String currentSubtitleUrl;
    private boolean skipSubtitle;

    // 续播相关
    private String currentVideoUrl;          // 当前播放的视频 URL（用于 Room 操作）
    private boolean hasPendingRestart;       // 是否处于可从头播放的 5 秒窗口
    private static final long RESTART_WINDOW_MS = 5000;

    // 时长限制相关
    private boolean pendingCooldownExit;     // 当前视频播完是否要因时长限额而退出

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        path = "/";
        if (args != null) {
            path = args.getString(ARG_PATH, "/");
            currentIndex = args.getInt(ARG_INDEX, 0);
            PlaybackQueueViewModel queue = new ViewModelProvider(requireActivity())
                    .get(PlaybackQueueViewModel.class);
            videoList = queue.getVideos();
            gridPositions = queue.getGridPositions();
        }
        if (savedInstanceState != null) {
            currentIndex = savedInstanceState.getInt("current_index", currentIndex);
            restorePaused = savedInstanceState.getBoolean("paused", false);
            restoredVideoFile = savedInstanceState.getString("current_video_file");
            restoredPositionMs = savedInstanceState.getLong("position_ms", -1);
        }
        int restoredIndex = findRestoredIndex(videoList);
        if (restoredIndex < 0) {
            videoList.clear();
            gridPositions.clear();
        } else {
            currentIndex = restoredIndex;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt("current_index", currentIndex);
        outState.putBoolean("paused", pendingInitialPlayback ? restorePaused
                : !wantsPlayback() && !resumeAfterLifecyclePause);
        outState.putLong("position_ms", pendingInitialPlayback ? restoredPositionMs
                : resumeLookupPending ? -1 : ExoPlayerManager.getCurrentPosition());
        if (currentIndex >= 0 && currentIndex < videoList.size()) {
            outState.putString("current_video_file", videoList.get(currentIndex).url);
        } else if (restoredVideoFile != null) {
            outState.putString("current_video_file", restoredVideoFile);
        }
        super.onSaveInstanceState(outState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_player, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        playerView = view.findViewById(R.id.player_view);
        tvTitle = view.findViewById(R.id.tv_video_title);
        tvSeekIndicator = view.findViewById(R.id.tv_seek_indicator);
        tvRestartHint = view.findViewById(R.id.tv_restart_hint);
        bottomProgressContainer = view.findViewById(R.id.bottom_progress_container);
        progressBar = view.findViewById(R.id.progress_bar);
        tvProgressCurrent = view.findViewById(R.id.tv_progress_current);
        tvProgressTotal = view.findViewById(R.id.tv_progress_total);
        retry = view.findViewById(R.id.btn_retry_video);
        retry.setOnClickListener(v -> retryPlayback());

        pendingInitialPlayback = true;
        if (videoList.isEmpty()) {
            restoreQueue();
        }
    }

    private void startCurrentVideo() {
        if (!isResumed() || !pendingInitialPlayback || videoList.isEmpty()) return;
        pendingInitialPlayback = false;
        playCurrent();
    }

    private void showQueueRestoreError() {
        handler.post(() -> {
            if (isResumed() && queueRestoreError != null) {
                Toast.makeText(getContext(), queueRestoreError, Toast.LENGTH_LONG).show();
                queueRestoreError = null;
                requireActivity().onBackPressed();
            }
        });
    }

    private void restoreQueue() {
        queueLoadFailed = false;
        int generation = playbackGeneration;
        queueRequest = new FileRepository().loadFiles(path, new FileRepository.FileCallback() {
            @Override public void onSuccess(List<FileModel> files) {
                handler.post(() -> {
                    if (getView() == null || generation != playbackGeneration) return;
                    queueRequest = null;
                    PlaybackQueueViewModel queue = new ViewModelProvider(requireActivity())
                            .get(PlaybackQueueViewModel.class);
                    queue.setFiles(files);
                    ArrayList<FileModel> videos = queue.getVideos();
                    int index = findRestoredIndex(videos);
                    if (index < 0) {
                        queueRestoreError = "无法恢复播放列表";
                        showQueueRestoreError();
                        return;
                    }
                    currentIndex = index;
                    videoList = videos;
                    gridPositions = queue.getGridPositions();
                    startCurrentVideo();
                });
            }
            @Override public void onError(IOException error) {
                handler.post(() -> {
                    if (getView() == null || generation != playbackGeneration) return;
                    queueRequest = null;
                    queueLoadFailed = true;
                    showRetry(R.string.retry_playlist);
                });
            }
        });
    }

    private int findRestoredIndex(List<FileModel> videos) {
        if (currentIndex >= 0 && currentIndex < videos.size()
                && (restoredVideoFile == null || restoredVideoFile.equals(videos.get(currentIndex).url))) {
            return currentIndex;
        }
        if (restoredVideoFile != null) {
            for (int i = 0; i < videos.size(); i++) {
                if (restoredVideoFile.equals(videos.get(i).url)) return i;
            }
        }
        return -1;
    }

    @OptIn(markerClass = UnstableApi.class)
    private void setupExoPlayer() {
        ExoPlayerManager.getInstance(requireContext());

        playerListener = new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (isSubtitleHttpError(error)) {
                    long position = resumeLookupPending ? -1 : ExoPlayerManager.getCurrentPosition();
                    boolean paused = !wantsPlayback();
                    int generation = playbackGeneration;
                    handler.post(() -> {
                        if (!isResumed() || generation != playbackGeneration) return;
                        skipSubtitle = true;
                        restoredPositionMs = position;
                        restorePaused = paused;
                        Toast.makeText(requireContext(), R.string.subtitle_unavailable, Toast.LENGTH_SHORT).show();
                        playCurrent();
                    });
                    return;
                }
                flushAccumulatedTime();
                playingSinceElapsed = -1;
                stopAccumulatedTimeTracking();
                resumeAfterLifecyclePause = false;
                playAfterResumeLookup = false;
                if (getContext() != null) {
                    ParentalControlManager.recordPlaybackPause(requireContext());
                }
                ExoPlayerManager.pause();
                showRetry(R.string.retry_playback);
            }
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    onPlaybackCompleted();
                } else if (playbackState == Player.STATE_READY) {
                    updateProgressInfo();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    ParentalControlManager.markPlaybackActive(requireContext());
                    playingSinceElapsed = SystemClock.elapsedRealtime();
                    startAccumulatedTimeTracking();
                } else {
                    flushAccumulatedTime();
                    playingSinceElapsed = -1;
                    stopAccumulatedTimeTracking();
                }
                titleShouldShow = !isPlaying;
                updateTitleVisibility();
                if (isPlaying) {
                    cancelTitleHide();
                    showProgress(false);
                    startPeriodicSave();
                } else {
                    scheduleTitleHide();
                    showProgress(true);
                    stopPeriodicSave();
                    saveCurrentProgress();
                }
            }
        };
        ExoPlayerManager.addListener(playerListener);

        playerView.setPlayer(ExoPlayerManager.getInstance(requireContext()));
        playerView.setUseController(false);
        playerView.setKeepContentOnPlayerReset(true);
    }

    private void playCurrent() {
        if (currentIndex < 0 || currentIndex >= videoList.size()) return;

        FileModel item = videoList.get(currentIndex);
        String name = item.name;
        String url = Urls.buildEncodedFileUrl(path, item.url);
        if (!url.equals(currentVideoUrl)) skipSubtitle = false;
        currentSubtitleUrl = skipSubtitle || item.subtitle == null || item.subtitle.trim().isEmpty()
                ? null : Urls.buildEncodedFileUrl(path, item.subtitle);
        currentVideoUrl = url;
        playbackGeneration++;
        clearResumeTimeout();
        cancelRestartHint();
        hasPendingRestart = false;
        tvRestartHint.setVisibility(View.GONE);

        tvTitle.setText(name);

        boolean startPlaying = !restorePaused;
        long startPosition = restoredPositionMs;
        restorePaused = false;
        restoredPositionMs = -1;
        // 暂停状态的页面恢复不应清除休息起点。
        if (startPlaying ? !ParentalControlManager.tryStartPlayback(requireContext())
                : ParentalControlManager.isInCooldown(requireContext())) {
            String remaining = ParentalControlManager.getCooldownRemainingText(requireContext());
            queueRestoreError = "休息时间还没结束，请" + remaining + "之后再回来~";
            showQueueRestoreError();
            return;
        }
        pendingCooldownExit = ParentalControlManager.isLimitReached(requireContext());
        if (currentIndex < gridPositions.size()) {
            SharedPrefsHelper.putLastPosition(requireContext(), path, gridPositions.get(currentIndex));
        }

        titleShouldShow = true;
        updateTitleVisibility();
        scheduleTitleHide();

        retry.setVisibility(View.GONE);
        restorePlaybackFocus();
        if (playerListener == null) setupExoPlayer();
        resumeLookupPending = startPosition < 0;
        playAfterResumeLookup = startPlaying;
        ExoPlayerManager.play(requireContext(), url, currentSubtitleUrl,
                Math.max(0, startPosition), startPlaying && !resumeLookupPending);
        if (resumeLookupPending) checkResumePlayback();
    }

    @OptIn(markerClass = UnstableApi.class)
    private boolean isSubtitleHttpError(PlaybackException error) {
        if (currentSubtitleUrl == null) return false;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpDataSource.HttpDataSourceException) {
                HttpDataSource.HttpDataSourceException httpError = (HttpDataSource.HttpDataSourceException) cause;
                return currentSubtitleUrl.equals(httpError.dataSpec.uri.toString());
            }
        }
        return false;
    }

    private void showRetry(int label) {
        retry.setText(label);
        retry.setVisibility(View.VISIBLE);
        retry.requestFocus();
    }

    private void restorePlaybackFocus() {
        playerView.setFocusable(true);
        playerView.requestFocus();
    }

    private void retryPlayback() {
        if (queueLoadFailed) {
            retry.setVisibility(View.GONE);
            restoreQueue();
        } else {
            resumeIfAllowed();
        }
    }

    // ===================== 续播逻辑 =====================

    private void checkResumePlayback() {
        Context appContext = requireContext().getApplicationContext();
        String videoUrl = currentVideoUrl;
        int generation = playbackGeneration;

        resumeLookupTimeout = () -> finishResumeLookup(generation, null);
        handler.postDelayed(resumeLookupTimeout, RESUME_LOOKUP_TIMEOUT_MS);
        AppDatabase.getDbExecutor().execute(() -> {
            VideoProgressEntity saved = null;
            try {
                saved = AppDatabase.getInstance(appContext).videoProgressDao().getProgress(videoUrl);
            } catch (RuntimeException e) {
                Log.w(TAG, "无法读取续播进度", e);
            }
            VideoProgressEntity progress = saved;
            handler.post(() -> finishResumeLookup(generation, progress));
        });
    }

    private void finishResumeLookup(int generation, @Nullable VideoProgressEntity saved) {
        if (!resumeLookupPending || generation != playbackGeneration || getView() == null) return;
        clearResumeTimeout();
        resumeLookupPending = false;
        if (saved != null && saved.getPositionMs() > 0
                && saved.getDurationMs() - saved.getPositionMs() > 10_000) {
            ExoPlayerManager.seekTo(saved.getPositionMs());
            showRestartHint(saved.getPositionMs(), saved.getDurationMs());
        }
        if (playAfterResumeLookup && isResumed()) ExoPlayerManager.resume();
    }

    private void clearResumeTimeout() {
        if (resumeLookupTimeout != null) {
            handler.removeCallbacks(resumeLookupTimeout);
            resumeLookupTimeout = null;
        }
    }

    /**
     * 显示续播提示 + 启动 5 秒从头播放窗口
     */
    private void showRestartHint(long savedPos, long duration) {
        if (getContext() == null) return;

        // 检查提示是否已满 10 次
        int hintCount = SharedPrefsHelper.getInt(getContext(),
                SharedPrefsHelper.KEY_RESTART_HINT_COUNT, 0);

        String remaining = formatTime(duration - savedPos);
        tvRestartHint.setText("剩余" + remaining + "，按左键从头播放");
        tvRestartHint.setVisibility(View.VISIBLE);

        if (hintCount < SharedPrefsHelper.KEY_RESTART_HINT_MAX) {
            // 计数 + 保存
            SharedPrefsHelper.putInt(getContext(),
                    SharedPrefsHelper.KEY_RESTART_HINT_COUNT, hintCount + 1);
        }

        // 进入 5 秒可重播窗口
        hasPendingRestart = true;

        cancelRestartHint();
        restartHintRunnable = () -> {
            hasPendingRestart = false;
            tvRestartHint.setVisibility(View.GONE);
        };
        handler.postDelayed(restartHintRunnable, RESTART_WINDOW_MS);
    }

    private void cancelRestartHint() {
        if (restartHintRunnable != null) {
            handler.removeCallbacks(restartHintRunnable);
            restartHintRunnable = null;
        }
    }

    /**
     * 从头开始播放（按左键时触发）
     */
    private void restartFromBeginning() {
        hasPendingRestart = false;
        cancelRestartHint();
        tvRestartHint.setVisibility(View.GONE);

        playbackGeneration++;
        resumeLookupPending = false;
        deleteProgress();
        ExoPlayerManager.seekTo(0);
        Toast.makeText(getContext(), "已从头播放", Toast.LENGTH_SHORT).show();
    }

    // ===================== 进度保存 =====================

    private void saveCurrentProgress() {
        // 查询尚未完成时，临时播放位置不能覆盖数据库中的续播位置。
        if (resumeLookupPending) return;
        Context context = getContext();
        if (currentVideoUrl == null || context == null) return;
        Context appContext = context.getApplicationContext();
        String videoUrl = currentVideoUrl;

        long pos = ExoPlayerManager.getCurrentPosition();
        long duration = ExoPlayerManager.getDuration();

        // 只有非空、合理的位置才保存
        if (pos <= 0 || duration <= 0) return;
        // 剩余不足 10 秒视为快播完了，不保存（下次从头播）
        if (duration - pos <= 10_000) {
            deleteProgress();
            return;
        }

        long finalPos = pos;
        long finalDuration = duration;
        writeProgress(appContext, dao -> dao.insertOrUpdate(new VideoProgressEntity(
                videoUrl, finalPos, finalDuration, System.currentTimeMillis())));
    }

    private void startPeriodicSave() {
        stopPeriodicSave();
        saveProgressRunnable = () -> {
            saveCurrentProgress();
            handler.postDelayed(saveProgressRunnable, 5000);
        };
        handler.postDelayed(saveProgressRunnable, 5000);
    }

    private void stopPeriodicSave() {
        if (saveProgressRunnable != null) {
            handler.removeCallbacks(saveProgressRunnable);
            saveProgressRunnable = null;
        }
    }

    private void deleteProgress() {
        Context context = getContext();
        if (currentVideoUrl == null || context == null) return;
        Context appContext = context.getApplicationContext();
        String videoUrl = currentVideoUrl;

        writeProgress(appContext, dao -> dao.delete(videoUrl));
    }

    private void writeProgress(Context appContext, Consumer<VideoProgressDao> write) {
        AppDatabase.getDbExecutor().execute(() -> {
            try {
                write.accept(AppDatabase.getInstance(appContext).videoProgressDao());
            } catch (RuntimeException e) {
                Log.w(TAG, "无法更新续播进度", e);
            }
        });
    }

    // ===================== 时长累计 =====================

    /**
     * 定期记录实际播放时间，达到上限后等待当前视频结束。
     */
    private void startAccumulatedTimeTracking() {
        stopAccumulatedTimeTracking();
        accumulatedTimeUpdater = () -> {
            if (ExoPlayerManager.isPlaying()) flushAccumulatedTime();
            handler.postDelayed(accumulatedTimeUpdater,
                    SharedPrefsHelper.TRACKING_INTERVAL_MS);
        };
        handler.postDelayed(accumulatedTimeUpdater,
                SharedPrefsHelper.TRACKING_INTERVAL_MS);
    }

    private void flushAccumulatedTime() {
        if (playingSinceElapsed < 0) return;
        long now = SystemClock.elapsedRealtime();
        long elapsed = Math.max(0, now - playingSinceElapsed);
        playingSinceElapsed = now;
        Context context = getContext();
        if (context != null && elapsed > 0) {
            ParentalControlManager.addAccumulatedTime(context, elapsed);
            if (ParentalControlManager.isLimitReached(context)) pendingCooldownExit = true;
        }
    }

    private void stopAccumulatedTimeTracking() {
        if (accumulatedTimeUpdater != null) {
            handler.removeCallbacks(accumulatedTimeUpdater);
            accumulatedTimeUpdater = null;
        }
    }

    // ===================== 播放完成 =====================

    private void onPlaybackCompleted() {
        if (pendingInitialPlayback || currentVideoUrl == null || !ExoPlayerManager.hasMediaItem()) return;
        flushAccumulatedTime();
        playingSinceElapsed = -1;
        stopAccumulatedTimeTracking();
        deleteProgress();

        if (pendingCooldownExit) {
            // 因时长限额而退出 → 启动冷却
            pendingCooldownExit = false;
            ParentalControlManager.startCooldown(requireContext());
            Toast.makeText(getContext(), "小朋友，该休息了，2个小时后可再次观看~", Toast.LENGTH_LONG).show();
            releasePlayer();
            requireActivity().onBackPressed();
            return;
        }

        ParentalControlManager.finishPlaybackTracking(requireContext());
        int playMode = ParentalControlManager.getPlayMode(requireContext());

        if (playMode == 0) {
            int nextIndex = findNextVideoIndex();
            if (nextIndex < 0) {
                Toast.makeText(getContext(), "播放完毕", Toast.LENGTH_SHORT).show();
                releasePlayer();
                requireActivity().onBackPressed();
                return;
            }
            currentIndex = nextIndex;
            playCurrent();
        } else {
            ExoPlayerManager.getInstance(requireContext()).seekTo(0);
            ExoPlayerManager.getInstance(requireContext()).play();
        }
    }

    private int findNextVideoIndex() {
        for (int i = currentIndex + 1; i < videoList.size(); i++) {
            if (videoList.get(i).isVideo() && videoList.get(i).url != null
                    && !videoList.get(i).url.isEmpty()) {
                return i;
            }
        }
        for (int i = 0; i < currentIndex; i++) {
            if (videoList.get(i).isVideo() && videoList.get(i).url != null
                    && !videoList.get(i).url.isEmpty()) {
                return i;
            }
        }
        return currentIndex >= 0 && currentIndex < videoList.size()
                && videoList.get(currentIndex).isVideo() ? currentIndex : -1;
    }

    // ===================== 标题显示控制 =====================

    private void scheduleTitleHide() {
        cancelTitleHide();
        hideTitleRunnable = () -> {
            titleShouldShow = false;
            updateTitleVisibility();
        };
        handler.postDelayed(hideTitleRunnable, 5000);
    }

    private void cancelTitleHide() {
        if (hideTitleRunnable != null) {
            handler.removeCallbacks(hideTitleRunnable);
            hideTitleRunnable = null;
        }
    }

    private void updateTitleVisibility() {
        tvTitle.setVisibility(titleShouldShow && tvSeekIndicator.getVisibility() != View.VISIBLE
                ? View.VISIBLE : View.GONE);
    }

    // ===================== 快进/快退指示器控制 =====================

    private void showSeekIndicator(String text) {
        tvSeekIndicator.setText(text);
        tvSeekIndicator.setVisibility(View.VISIBLE);
        updateTitleVisibility();

        showProgress(true);
        updateProgressInfo();

        cancelSeekHide();
        hideSeekRunnable = () -> {
            tvSeekIndicator.setVisibility(View.GONE);
            updateTitleVisibility();
        };
        handler.postDelayed(hideSeekRunnable, 800);
    }

    private void cancelSeekHide() {
        if (hideSeekRunnable != null) {
            handler.removeCallbacks(hideSeekRunnable);
            hideSeekRunnable = null;
        }
    }

    // ===================== 进度条控制 =====================

    private void showProgress(boolean show) {
        if (isProgressVisible == show) return;
        isProgressVisible = show;

        bottomProgressContainer.setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            updateProgressInfo();
            startProgressUpdates();
        } else {
            stopProgressUpdates();
        }
    }

    private void startProgressUpdates() {
        stopProgressUpdates();
        progressUpdater = () -> {
            if (isProgressVisible) {
                updateProgressInfo();
                handler.postDelayed(progressUpdater, 500);
            }
        };
        handler.postDelayed(progressUpdater, 500);
    }

    private void stopProgressUpdates() {
        if (progressUpdater != null) {
            handler.removeCallbacks(progressUpdater);
            progressUpdater = null;
        }
    }

    private void updateProgressInfo() {
        long duration = ExoPlayerManager.getDuration();
        long currentPos = ExoPlayerManager.getCurrentPosition();

        if (duration <= 0) return;

        int progress = (int) (currentPos * 1000 / duration);
        progressBar.setProgress(progress);

        tvProgressCurrent.setText(formatTime(currentPos));
        tvProgressTotal.setText(formatTime(duration));
    }

    private static String formatTime(long ms) {
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;

        if (h > 0) {
            return String.format("%02d:%02d:%02d", h, m, s);
        } else {
            return String.format("%02d:%02d", m, s);
        }
    }

    // ===================== 遥控器按键处理 =====================

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean confirm = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean playbackKey = confirm || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (confirm && event.getRepeatCount() > 0) return true;
        if (confirm && retry != null && retry.getVisibility() == View.VISIBLE) {
            retryPlayback();
            return true;
        }
        if (playbackKey && (pendingInitialPlayback || !ExoPlayerManager.hasMediaItem())) return true;
        if (confirm) {
            if (wantsPlayback()) {
                flushAccumulatedTime();
                playingSinceElapsed = -1;
                stopAccumulatedTimeTracking();
                ParentalControlManager.recordPlaybackPause(requireContext());
                playAfterResumeLookup = false;
                ExoPlayerManager.pause();
            } else {
                resumeIfAllowed();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            // 处于续播窗口内 → 从头播放
            if (hasPendingRestart) {
                restartFromBeginning();
                return true;
            }
            // 否则正常快退
            boolean resume = cancelPendingResumeLookup();
            ExoPlayerManager.seekBack();
            if (resume) resumeIfAllowed();
            showSeekIndicator("快退");
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            boolean resume = cancelPendingResumeLookup();
            ExoPlayerManager.seekForward();
            if (resume) resumeIfAllowed();
            showSeekIndicator("快进");
            return true;
        }
        return false;
    }

    private boolean wantsPlayback() {
        return resumeLookupPending ? playAfterResumeLookup : ExoPlayerManager.wantsToPlay();
    }

    private boolean cancelPendingResumeLookup() {
        if (!resumeLookupPending) return false;
        resumeLookupPending = false;
        clearResumeTimeout();
        return playAfterResumeLookup;
    }

    // ===================== 生命周期 =====================

    @Override
    public void onPause() {
        super.onPause();
        saveCurrentProgress();
        flushAccumulatedTime();
        playingSinceElapsed = -1;
        stopAccumulatedTimeTracking();
        resumeAfterLifecyclePause = !pendingInitialPlayback && wantsPlayback();
        Activity activity = getActivity();
        if (playerListener != null && ExoPlayerManager.hasMediaItem()
                && (activity == null || !activity.isChangingConfigurations())) {
            ParentalControlManager.recordPlaybackPause(requireContext());
        }
        ExoPlayerManager.pause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (queueRestoreError != null) {
            showQueueRestoreError();
            return;
        }
        if (pendingInitialPlayback) {
            startCurrentVideo();
            return;
        }
        if (resumeAfterLifecyclePause) {
            resumeAfterLifecyclePause = false;
            resumeIfAllowed();
        }
    }

    private void resumeIfAllowed() {
        if (pendingInitialPlayback || !ExoPlayerManager.hasMediaItem()) return;
        Context context = getContext();
        if (context == null) return;
        if (!ParentalControlManager.tryStartPlayback(context)) {
            Toast.makeText(context, "休息时间还没结束，请"
                    + ParentalControlManager.getCooldownRemainingText(context) + "之后再回来~",
                    Toast.LENGTH_LONG).show();
            return;
        }
        pendingCooldownExit = ParentalControlManager.isLimitReached(context);
        retry.setVisibility(View.GONE);
        restorePlaybackFocus();
        if (resumeLookupPending) {
            playAfterResumeLookup = true;
            if (ExoPlayerManager.getInstance(context).getPlaybackState() == Player.STATE_IDLE) {
                ExoPlayerManager.getInstance(context).prepare();
            }
        } else {
            ExoPlayerManager.resume();
        }
    }

    @Override
    public void onStop() {
        if (playerListener != null && ExoPlayerManager.hasMediaItem()) {
            restoredPositionMs = resumeLookupPending ? -1 : ExoPlayerManager.getCurrentPosition();
            restorePaused = !resumeAfterLifecyclePause;
            resumeAfterLifecyclePause = false;
            pendingInitialPlayback = true;
        }
        releasePlayer();
        showProgress(false);
        super.onStop();
    }

    private void releasePlayer() {
        if (playerListener == null) return;
        playbackGeneration++;
        resumeLookupPending = false;
        clearResumeTimeout();
        ExoPlayerManager.removeListener(playerListener);
        playerListener = null;
        playerView.setPlayer(null);
        ExoPlayerManager.release();
        cancelTitleHide();
        cancelSeekHide();
        cancelRestartHint();
        stopProgressUpdates();
        stopPeriodicSave();
        stopAccumulatedTimeTracking();
    }

    @Override
    public void onDestroyView() {
        releasePlayer();
        playbackGeneration++;
        if (queueRequest != null) {
            queueRequest.cancel();
            queueRequest = null;
        }
        handler.removeCallbacksAndMessages(null);
        playerView = null;
        retry = null;
        super.onDestroyView();
    }
}
