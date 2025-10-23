package com.neovision.onframe;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import android.graphics.drawable.Drawable;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 전체화면 슬라이드쇼 (두 개의 ImageView를 겹쳐서 매 전환마다 알파 페이드)
 * - AlbumStore 설정(표시시간, 페이드시간, 랜덤/사용자순서) 반영
 */
public class AlbumFragment extends Fragment {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable slideTask = this::showNext;

    private ImageView front; // 현재 화면에 보이는 쪽
    private ImageView back;  // 다음 이미지를 미리 그려둘 쪽
    private TextView emptyView;

    private List<String> images = new ArrayList<>();
    private int index = 0;

    private int showSec = 5;
    private int fadeSec = 2;
    private boolean shuffle = false;

    private boolean frontIsA = true;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle s) {
        FrameLayout root = new FrameLayout(requireContext());
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView a = new ImageView(requireContext());
        a.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        a.setScaleType(ImageView.ScaleType.CENTER_CROP);

        ImageView b = new ImageView(requireContext());
        b.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        b.setScaleType(ImageView.ScaleType.CENTER_CROP);

        // 쌓는 순서: 아래 back, 위 front
        root.addView(a);
        root.addView(b);

        front = b;
        back  = a;

        emptyView = new TextView(requireContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        emptyView.setLayoutParams(lp);
        emptyView.setText("이미지를 추가해 주세요");
        emptyView.setAlpha(0.6f);
        emptyView.setTextSize(18f);
        emptyView.setVisibility(View.GONE);

        root.addView(emptyView);

        return root;
    }

    @Override public void onResume() {
        super.onResume();
        reloadConfigAndStart();
    }

    @Override public void onPause() {
        super.onPause();
        handler.removeCallbacks(slideTask);
    }

    private void reloadConfigAndStart() {
        handler.removeCallbacks(slideTask);

        images = AlbumStore.getImages(requireContext());
        showSec = Math.max(1, AlbumStore.getShowSec(requireContext()));
        fadeSec = Math.max(0, AlbumStore.getFadeSec(requireContext()));
        shuffle = AlbumStore.isShuffle(requireContext());

        if (images == null) images = new ArrayList<>();
        if (images.isEmpty()) {
            front.setVisibility(View.GONE);
            back.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            return;
        }

        emptyView.setVisibility(View.GONE);
        front.setVisibility(View.VISIBLE);
        back.setVisibility(View.VISIBLE);

        if (shuffle) {
            images = new ArrayList<>(images);
            Collections.shuffle(images);
        }
        index = index % images.size();

        // 첫 장은 페이드 없이 front에 바로 세팅
        loadInto(front, images.get(index), false, 0, null);

        scheduleNext(showSec * 1000L);
    }

    private void showNext() {
        if (!isAdded() || images.isEmpty()) return;
        index = (index + 1) % images.size();

        final int fadeMs = Math.max(0, fadeSec * 1000);

        // 다음 이미지를 back에 로드 → 로드 완료 시 front/back를 알파로 교차
        loadInto(back, images.get(index), true, fadeMs, () -> {
            if (!isAdded()) return;

            // 교차 페이드
            back.setAlpha(0f);
            back.animate().alpha(1f).setDuration(fadeMs).start();
            front.animate().alpha(0f).setDuration(fadeMs).withEndAction(() -> {
                // front를 back과 교체
                ImageView tmp = front;
                front = back;
                back  = tmp;

                // 다음 전환 대비 초기화
                back.setAlpha(1f);
                front.bringToFront();

                scheduleNext(showSec * 1000L);
            }).start();
        });
    }

    private void scheduleNext(long delayMs) {
        handler.removeCallbacks(slideTask);
        handler.postDelayed(slideTask, delayMs);
    }

    /**
     * Glide로 Drawable을 받아서 원하는 ImageView에 세팅.
     * onReady 콜백으로 로드 완료 타이밍에 페이드 트리거 가능.
     */
    private void loadInto(ImageView target, String src, boolean waitReady, int fadeMs, @Nullable Runnable onReady) {
        Object model = src;
        if (src != null) {
            if (src.startsWith("/")) model = "file://" + src;
            else if (src.startsWith("content://") || src.startsWith("file://")) model = src;
        }

        if (waitReady) {
            Glide.with(this)
                    .load(model)
                    .into(new CustomTarget<Drawable>() {
                        @Override public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                            target.setImageDrawable(resource);
                            if (onReady != null) onReady.run();
                        }
                        @Override public void onLoadCleared(@Nullable Drawable placeholder) { }
                    });
        } else {
            Glide.with(this).load(model).into(target);
        }
    }
}