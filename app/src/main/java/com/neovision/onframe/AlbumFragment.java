// AlbumFragment.java
package com.neovision.onframe;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException; // ★ 추가된 임포트
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;
import java.util.List;

/**
 * 앨범 화면(슬라이드쇼)
 * - 두 개의 ImageView를 겹쳐서 '우리가 직접' 크로스페이드
 * - 다음 이미지는 미리 로드가 완료된 때에만 페이드 시작 → 깜빡임/블랙 방지
 * - 매 사이클마다 설정값(표시시간/페이드/셔플)을 다시 읽어 즉시 반영
 * - 항상 CENTER_CROP 풀스크린
 */
public class AlbumFragment extends Fragment {

    // === Prefs fallback (AlbumStore 없을 때) ===
    private static final String PREFS      = "album_prefs";
    private static final String KEY_IMAGES = "album_images_csv"; // "uri||uri||..."
    private static final String KEY_SHOW   = "album_show_sec";   // int seconds
    private static final String KEY_FADE   = "album_fade_sec";   // float seconds
    private static final String KEY_SHUFFLE= "album_shuffle";    // boolean

    // 뷰(더블 버퍼)
    private FrameLayout root;
    private ImageView ivA, ivB;
    private boolean showingA = true;

    // 상태
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Uri> images = new ArrayList<>();
    private int index = 0;
    private boolean running = false;

    // 로딩 상태
    private boolean nextReady = false;

    // 스케줄용 runnable
    private final Runnable advanceRunnable = new Runnable() {
        @Override public void run() {
            if (!running || images.isEmpty() || !isAdded()) return;
            int showMs = Math.max(200, loadShowMs(requireContext()));
            int fadeMs = Math.max(0,   loadFadeMs(requireContext()));

            if (nextReady) {
                crossfadeToNext(fadeMs);
            } else {
                handler.postDelayed(this, 50);
            }
        }
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        root = new FrameLayout(requireContext());
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.BLACK);

        ivA = createImageView();
        ivB = createImageView();

        root.addView(ivA);
        root.addView(ivB);

        ivA.setAlpha(0f);
        ivB.setAlpha(0f);
        return root;
    }

    private ImageView createImageView() {
        ImageView iv = new ImageView(requireContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        );
        iv.setLayoutParams(lp);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return iv;
    }

    @Override public void onResume() {
        super.onResume();
        startSlideShow();
    }

    @Override public void onPause() {
        super.onPause();
        stopSlideShow();
    }

    private void startSlideShow() {
        if (running) return;
        images.clear();
        images.addAll(loadImagesCompat(requireContext()));
        if (images.isEmpty()) return;

        running = true;
        index = 0;
        showingA = true;
        nextReady = false;

        Glide.with(this).clear(ivA);
        Glide.with(this).clear(ivB);
        ivA.setAlpha(0f);
        ivB.setAlpha(0f);

        preloadNext(index);
    }

    private void stopSlideShow() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (isAdded()) {
            Glide.with(this).clear(ivA);
            Glide.with(this).clear(ivB);
        }
        ivA.animate().cancel();
        ivB.animate().cancel();
    }

    private void preloadNext(int curIndex) {
        if (!running || images.isEmpty()) return;

        final Uri cur = images.get(curIndex);
        final ImageView curView   = showingA ? ivA : ivB;
        final ImageView hiddenView= showingA ? ivB : ivA;

        nextReady = false;

        Glide.with(this)
                .load(cur)
                .thumbnail(0.25f)
                .dontAnimate()
                .centerCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        curView.setAlpha(1f);
                        scheduleNextCycle();
                        return false;
                    }
                    @Override public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        curView.setAlpha(1f);
                        scheduleNextCycle();
                        return false;
                    }
                })
                .into(curView);

        int nxt = (curIndex + 1) % images.size();
        final Uri next = images.get(nxt);

        Glide.with(this)
                .load(next)
                .dontAnimate()
                .centerCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        nextReady = true;
                        return false;
                    }
                    @Override public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        nextReady = true;
                        return false;
                    }
                })
                .into(hiddenView);
    }

    private void scheduleNextCycle() {
        if (!running) return;
        int showMs = Math.max(200, loadShowMs(requireContext()));
        handler.removeCallbacks(advanceRunnable);
        handler.postDelayed(advanceRunnable, showMs);
    }

    private void crossfadeToNext(int fadeMs) {
        if (!running || images.isEmpty()) return;

        final ImageView curView  = showingA ? ivA : ivB;
        final ImageView nextView = showingA ? ivB : ivA;

        if (fadeMs <= 0) {
            curView.setAlpha(0f);
            nextView.setAlpha(1f);
            stepIndexAndPreload();
            return;
        }

        curView.animate().cancel();
        nextView.animate().cancel();

        nextView.setAlpha(0f);
        nextView.animate()
                .alpha(1f)
                .setDuration(fadeMs)
                .setInterpolator(new LinearInterpolator())
                .start();

        curView.animate()
                .alpha(0f)
                .setDuration(fadeMs)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(this::stepIndexAndPreload)
                .start();
    }

    private void stepIndexAndPreload() {
        boolean shuffle = loadShuffle(requireContext());
        if (shuffle && images.size() > 2) {
            List<Uri> pool = new ArrayList<>(images);
            Uri cur = images.get(index);
            pool.remove(cur);
            int nextIdx = (int) (Math.random() * pool.size());
            Uri next = pool.get(nextIdx);
            index = images.indexOf(next);
            if (index < 0) index = (images.indexOf(cur) + 1) % images.size();
        } else {
            index = (index + 1) % images.size();
        }

        showingA = !showingA;
        nextReady = false;

        preloadNext(index);
    }

    private int loadShowMs(Context ctx) {
        int sec = 5;
        try { sec = AlbumStore.getShowSeconds(ctx); }
        catch (Throwable ignore) {
            sec = prefs(ctx).getInt(KEY_SHOW, 5);
        }
        if (sec < 1) sec = 1;
        if (sec > 60) sec = 60;
        return sec * 1000;
    }

    private int loadFadeMs(Context ctx) {
        float sec = 1.0f;
        try { sec = AlbumStore.getFadeSeconds(ctx); }
        catch (Throwable ignore) {
            sec = prefs(ctx).getFloat(KEY_FADE, 1.0f);
        }
        if (sec < 0f) sec = 0f;
        if (sec > 10f) sec = 10f;
        return Math.round(sec * 1000f);
    }

    private boolean loadShuffle(Context ctx) {
        try { return AlbumStore.isShuffle(ctx); }
        catch (Throwable ignore) {
            return prefs(ctx).getBoolean(KEY_SHUFFLE, false);
        }
    }

    private List<Uri> loadImagesCompat(Context ctx) {
        ArrayList<Uri> out = new ArrayList<>();
        try {
            List<?> raw = AlbumStore.getImages(ctx);
            if (raw != null) {
                for (Object o : raw) {
                    if (o instanceof Uri) out.add((Uri) o);
                    else if (o instanceof String) try { out.add(Uri.parse((String) o)); } catch (Throwable ignore) {}
                }
            }
        } catch (Throwable ignore) { }
        if (!out.isEmpty()) return out;

        String csv = prefs(ctx).getString(KEY_IMAGES, "");
        if (csv != null && !csv.isEmpty()) {
            String[] parts = csv.split("\\|\\|");
            for (String s : parts) if (!s.isEmpty()) try { out.add(Uri.parse(s)); } catch (Throwable ignore) {}
        }
        return out;
    }

    private SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}