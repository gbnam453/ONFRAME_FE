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
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 앨범 화면 전용 프래그먼트
 * - 항상 전체화면(centerCrop)으로 이미지 표시
 * - 새 이미지는 백 레이어(imageB)에 먼저 로드 -> 로드 완료 후에만 크로스페이드 시작 → 이전 프레임이 보이는 깜빡임 제거
 * - 표시시간/페이드시간/셔플 설정은 AlbumStore에서 읽어옴
 */
public class AlbumFragment extends Fragment {

    private ImageView imageA;
    private ImageView imageB;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tick;

    private final ArrayList<Uri> images = new ArrayList<>();
    private boolean shuffle = false;
    private int showMs = 3000; // 기본값
    private int fadeMs = 500;  // 기본값

    // 셔플용
    private final Random random = new Random();
    private final ArrayList<Integer> order = new ArrayList<>();
    private int orderPos = 0;

    // 현재 화면에 보이는 레이어가 A인지 여부 (A가 앞: true)
    private boolean aIsFront = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inf.inflate(R.layout.fragment_album, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        imageA = v.findViewById(R.id.imageA);
        imageB = v.findViewById(R.id.imageB);

        // 첫 진입 시 alpha 초기화
        imageA.setAlpha(1f);
        imageB.setAlpha(0f);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 설정값 매번 새로 읽어서 즉시 반영
        loadConfigAndImages();
        startSlideshow();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopSlideshow();
        // 애니메이션/요청 정리
        if (imageA != null) { imageA.animate().cancel(); Glide.with(this).clear(imageA); }
        if (imageB != null) { imageB.animate().cancel(); Glide.with(this).clear(imageB); }
    }

    // --- 설정/이미지 로딩 ---

    private void loadConfigAndImages() {
        // 시간 설정
        try {
            int showSec = Math.max(1, AlbumStore.getShowSeconds(requireContext())); // 최소 1초
            float fadeSec = Math.max(0f, AlbumStore.getFadeSeconds(requireContext()));
            showMs = Math.max(200, showSec * 1000);
            fadeMs = Math.max(0, Math.round(fadeSec * 1000f));
        } catch (Throwable ignore) {}

        // 셔플 설정
        try {
            shuffle = AlbumStore.isShuffle(requireContext());
        } catch (Throwable ignore) {
            shuffle = false;
        }

        // 이미지 목록
        images.clear();
        try {
            List<Uri> saved = AlbumStore.getImages(requireContext());
            if (saved != null) images.addAll(saved);
        } catch (Throwable ignore) {}

        buildOrder();
    }

    private void buildOrder() {
        order.clear();
        for (int i = 0; i < images.size(); i++) order.add(i);
        if (shuffle) Collections.shuffle(order, random);
        orderPos = 0;
    }

    private int nextIndex() {
        if (images.isEmpty()) return -1;
        if (orderPos >= order.size()) {
            // 한 바퀴 돌았으면 다시 빌드(셔플은 다시 섞임)
            buildOrder();
        }
        return order.get(orderPos++);
    }

    // --- 슬라이드쇼 제어 ---

    private void startSlideshow() {
        stopSlideshow(); // 중복 방지
        if (images.isEmpty()) return;

        // 첫 프레임: front에 즉시 로드 (애니메이션 없음)
        final int first = nextIndex();
        if (first == -1) return;
        final ImageView front = frontView();
        final Uri u = images.get(first);

        front.animate().cancel();
        backView().animate().cancel();
        front.setAlpha(1f);
        backView().setAlpha(0f);

        Glide.with(this)
                .load(u)
                .centerCrop()
                .dontAnimate() // 우리가 직접 제어
                .into(front);

        scheduleNext(); // 다음 프레임 예약
    }

    private void stopSlideshow() {
        if (tick != null) {
            handler.removeCallbacks(tick);
            tick = null;
        }
    }

    private void scheduleNext() {
        stopSlideshow();
        tick = () -> {
            int ni = nextIndex();
            if (ni == -1 || !isAdded()) return;

            final Uri nextUri = images.get(ni);
            final ImageView oldFront = frontView();
            final ImageView incoming = backView();

            // 먼저 새 이미지를 백 레이어에 로드(비가시 alpha 0)
            incoming.animate().cancel();
            oldFront.animate().cancel();
            incoming.setAlpha(0f);

            Glide.with(this)
                    .load(nextUri)
                    .centerCrop()
                    .dontAnimate() // Glide 애니메이션 금지(깜빡임 방지)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            // 실패해도 다음 주기 예약 (끊김 방지)
                            swapWithoutFade();
                            return false; // 에러 placeholder 처리 Glide에게 맡김
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            // 리소스가 준비된 순간부터만 크로스페이드 시작
                            doCrossfade(oldFront, incoming);
                            return false; // 이미지 세팅은 Glide가 진행
                        }
                    })
                    .into(incoming);
        };
        handler.postDelayed(tick, showMs);
    }

    private void doCrossfade(ImageView oldFront, ImageView incoming) {
        if (!isAdded()) return;

        // incoming이 위에 오도록
        incoming.bringToFront();

        if (fadeMs <= 0) {
            // 페이드 없음: 즉시 전환
            incoming.setAlpha(1f);
            aIsFront = !aIsFront;
            // 이전 이미지 정리 (메모리/잔상 방지)
            Glide.with(this).clear(oldFront);
            scheduleNext();
            return;
        }

        // 크로스페이드
        incoming.animate().cancel();
        oldFront.animate().cancel();

        incoming.setAlpha(0f);
        incoming.animate()
                .alpha(1f)
                .setDuration(fadeMs)
                .withEndAction(() -> {
                    // 전환 완료 후 front 스와핑
                    aIsFront = !aIsFront;
                    // 이전 이미지 정리(가끔 보이던 잔상/플래시 방지)
                    Glide.with(this).clear(oldFront);
                    oldFront.setAlpha(1f); // 다음 전환 대비 초기화
                    scheduleNext();
                })
                .start();
    }

    private void swapWithoutFade() {
        // 로드 실패 시에도 쇼는 진행되도록 그냥 front 토글
        aIsFront = !aIsFront;
        scheduleNext();
    }

    private ImageView frontView() {
        return aIsFront ? imageA : imageB;
    }

    private ImageView backView() {
        return aIsFront ? imageB : imageA;
    }
}