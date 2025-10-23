package com.neovision.onframe;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 앨범 화면: 더블버퍼(이미지뷰 2장) + 선로딩으로 딜레이/깜빡임 없이 전체화면 슬라이드
 */
public class AlbumFragment extends Fragment {

    private ImageView imgA, imgB;
    private boolean showingA = true; // 현재 화면에 보이는 이미지뷰가 A인지 여부
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable advanceTask;
    private CustomTarget<Drawable> pendingTarget; // 진행 중 로딩 타겟 해제용

    private List<Uri> images = new ArrayList<>();
    private int index = -1;

    private int showMs = 5000;  // 한 장 표시 시간(ms)
    private int fadeMs = 800;   // 페이드 시간(ms)

    // 마지막으로 본 이미지를 약하게 기억해 두었다가 진입 즉시 붙여 '검은 화면' 제거
    private static WeakReference<Drawable> sLastDrawableRef;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_album, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        imgA = v.findViewById(R.id.imgA);
        imgB = v.findViewById(R.id.imgB);

        // 마지막 본 이미지가 있으면 즉시 붙여 첫 진입 딜레이 제거
        Drawable last = sLastDrawableRef != null ? sLastDrawableRef.get() : null;
        if (last != null) {
            imgA.setImageDrawable(last);
            imgA.setVisibility(View.VISIBLE);
            showingA = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSettings();
        loadImages();
        startSlideshow();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopSlideshow();
    }

    private void loadSettings() {
        try {
            int showSec  = AlbumStore.getShowSeconds(requireContext());
            float fadeSec = AlbumStore.getFadeSeconds(requireContext());
            showMs = Math.max(200, showSec * 1000);
            fadeMs = Math.max(0, Math.round(fadeSec * 1000f));
        } catch (Throwable ignore) {
            // 안전 기본값
            showMs = 5000;
            fadeMs = 800;
        }
    }

    private void loadImages() {
        try {
            List<Uri> list = AlbumStore.getImages(requireContext());
            images.clear();
            if (list != null) images.addAll(list);
            if (AlbumStore.isShuffle(requireContext())) {
                Collections.shuffle(images);
            }
        } catch (Throwable ignore) {
            images.clear();
        }
    }

    private void startSlideshow() {
        stopSlideshow(); // 중복 방지

        if (images.isEmpty()) {
            // 이미지 없음 → 두 뷰 모두 감추고 종료
            imgA.setVisibility(View.INVISIBLE);
            imgB.setVisibility(View.INVISIBLE);
            index = -1;
            return;
        }

        // 첫 장 세팅: 화면에 보이는 뷰에 즉시 로드(애니메이션 없이)
        if (index < 0) {
            index = 0;
            final ImageView cur = showingA ? imgA : imgB;
            cur.setVisibility(View.VISIBLE);
            loadInto(cur, images.get(index), /*notify*/ null);
        }

        scheduleNext();
    }

    private void stopSlideshow() {
        handler.removeCallbacksAndMessages(null);
        if (pendingTarget != null) {
            try { Glide.with(this).clear(pendingTarget); } catch (Throwable ignore) {}
            pendingTarget = null;
        }
    }

    private void scheduleNext() {
        if (advanceTask != null) handler.removeCallbacks(advanceTask);
        advanceTask = () -> {
            if (!isAdded() || images.isEmpty()) return;

            final int nextIndex = (index + 1) % images.size();
            final ImageView cur = showingA ? imgA : imgB;
            final ImageView next = showingA ? imgB : imgA;

            // 다음 장을 '보이지 않는 뷰'에 미리 로드
            pendingTarget = loadInto(next, images.get(nextIndex), () -> {
                // 로드가 끝난 시점에만 크로스페이드 → 깜빡임/검은 화면 방지
                crossfade(cur, next);
                index = nextIndex;
                showingA = !showingA;

                // 마지막 이미지 캐시(재진입 시 즉시 표시)
                Drawable d = next.getDrawable();
                sLastDrawableRef = d != null ? new WeakReference<>(d) : null;

                // 다음 스케줄
                scheduleNext();
            });
        };
        handler.postDelayed(advanceTask, showMs);
    }

    /**
     * 이미지를 target ImageView에 선로딩. 로드 완료 시 onReady 콜백.
     */
    private CustomTarget<Drawable> loadInto(@NonNull ImageView target, @NonNull Uri uri, @Nullable Runnable onReady) {
        // 로딩 중에도 현재 이미지는 그대로 보이므로 내부 애니메이션은 끄고(dontAnimate)
        // 빠르게 저해상도 썸네일을 먼저 붙여 체감속도 향상(thumbnail)
        CustomTarget<Drawable> t = new CustomTarget<Drawable>() {
            @Override public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                target.setImageDrawable(resource);
                if (onReady != null) onReady.run();
            }
            @Override public void onLoadCleared(@Nullable Drawable placeholder) { /* no-op */ }
        };

        Glide.with(this)
                .load(uri)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .priority(Priority.IMMEDIATE)
                .dontAnimate()
                .thumbnail(0.25f)
                .into(t);

        return t;
    }

    /**
     * 현재 뷰(cur) 위로 다음 뷰(next)를 페이드인. Glide 애니메이션은 쓰지 않고
     * 자체 alpha 애니메이션으로만 처리하여 깜빡임 없이 전환.
     */
    private void crossfade(@NonNull ImageView cur, @NonNull ImageView next) {
        if (fadeMs <= 0) {
            // 즉시 전환
            cur.setVisibility(View.INVISIBLE);
            next.setAlpha(1f);
            next.setVisibility(View.VISIBLE);
            return;
        }

        next.setAlpha(0f);
        next.setVisibility(View.VISIBLE);

        next.animate().alpha(1f).setDuration(fadeMs).start();
        cur.animate().alpha(0f).setDuration(fadeMs).withEndAction(() -> {
            cur.setVisibility(View.INVISIBLE);
            cur.setAlpha(1f); // 다음 전환 대비 원복
        }).start();
    }
}