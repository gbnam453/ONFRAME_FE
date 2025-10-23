package com.neovision.onframe;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 앨범 화면: 전체화면 이미지 슬라이드 + '진짜' 크로스페이드
 * - 두 개의 겹친 ImageView 사이를 교대로 페이드 (동시에 out/in)
 * - 다음 이미지가 "로드 완료"된 뒤에만 전환 시작 → 검정 화면/깜빡임 방지
 * - centerCrop 으로 화면 가득 표시
 */
public class AlbumFragment extends Fragment {

    // 설정 키 (AlbumSettingsFragment와 동일 키 사용)
    private static final String PREFS = "album_prefs";
    private static final String KEY_IMAGES   = "album_images";        // "uri||uri||..."
    private static final String KEY_SHOW_SEC = "album_show_seconds";  // int(초)
    private static final String KEY_FADE_SEC = "album_fade_seconds";  // float(초)
    private static final String KEY_SHUFFLE  = "album_shuffle";       // boolean

    private static final int    DEF_SHOW_SEC = 5;      // 기본 한장 표시 5초
    private static final float  DEF_FADE_SEC = 1.0f;   // 기본 페이드 1.0초

    private ImageView imgA, imgB;     // 두 장 레이어
    private ImageView front, back;     // 현재/다음 대상
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final List<Uri> uris = new ArrayList<>();
    private int index = -1;
    private boolean shuffle = false;

    private int showMs;   // 표시 시간(ms)
    private int fadeMs;   // 크로스페이드 시간(ms)

    private final Runnable nextTick = new Runnable() {
        @Override public void run() {
            if (!isAdded() || uris.isEmpty()) return;
            int next = (index + 1) % uris.size();
            crossFadeTo(uris.get(next));
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inf.inflate(R.layout.fragment_album, container, false);

        imgA = v.findViewById(R.id.imageA);
        imgB = v.findViewById(R.id.imageB);

        // 시작 상태: 둘 다 투명. 첫 로드가 끝나면 보이게 만든다.
        imgA.setAlpha(0f);
        imgB.setAlpha(0f);
        imgA.setVisibility(View.VISIBLE);
        imgB.setVisibility(View.VISIBLE);

        front = imgA;
        back  = imgB;

        loadConfig(v.getContext());
        loadImages(v.getContext());

        if (!uris.isEmpty()) {
            // 첫 장 '로드 완료' 후 바로 표시(페이드 없이)
            index = 0;
            preloadInto(front, uris.get(index), new Runnable() {
                @Override public void run() {
                    if (!isAdded()) return;
                    front.setAlpha(1f);   // 첫 장은 바로 보이게
                    scheduleNext();
                }
            });
        }

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        // Glide 요청 정리
        if (getContext() != null) {
            Glide.with(getContext()).clear(imgA);
            Glide.with(getContext()).clear(imgB);
        }
    }

    // -----------------------
    // Core: Cross-fade logic
    // -----------------------
    private void crossFadeTo(@NonNull Uri nextUri) {
        // 다음 이미지를 back 뷰에 "미리 로드"하고, 로드가 끝나면 애니메이션 시작
        preloadInto(back, nextUri, new Runnable() {
            @Override public void run() {
                if (!isAdded()) return;

                // 동시에 out/in (진짜 크로스페이드)
                back.setAlpha(0f);
                back.bringToFront();

                back.animate()
                        .alpha(1f)
                        .setDuration(fadeMs)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .withEndAction(new Runnable() {
                            @Override public void run() {
                                // 전환 완료 후 front/back 스왑
                                ImageView tmp = front;
                                front = back;
                                back  = tmp;

                                // back은 다음 전환을 위해 투명으로 재설정
                                back.setAlpha(0f);

                                // 인덱스 갱신 및 다음 예약
                                index = uris.indexOf(nextUri);
                                if (index < 0) index = 0; // 안전장치
                                scheduleNext();
                            }
                        })
                        .start();

                front.animate()
                        .alpha(0f)
                        .setDuration(fadeMs)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
            }
        });
    }

    private void scheduleNext() {
        handler.removeCallbacks(nextTick);
        handler.postDelayed(nextTick, showMs);
    }

    private void preloadInto(@NonNull ImageView target, @NonNull Uri uri, @Nullable Runnable onReady) {
        if (!isAdded()) return;
        Glide.with(this)
                .load(uri)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate() // 애니메이션은 우리가 직접 한다
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> t, boolean first) {
                        if (onReady != null) onReady.run(); // 실패해도 다음으로 넘어가도록
                        return false; // 에러 플레이스홀더 등 기본 동작 유지 X (없음)
                    }
                    @Override
                    public boolean onResourceReady(Drawable res, Object model, Target<Drawable> t, DataSource ds, boolean first) {
                        if (onReady != null) onReady.run();
                        return false; // Glide가 target에 set 하는 기본 동작 수행
                    }
                })
                .into(target);
    }

    // -----------------------
    // Config / Data loading
    // -----------------------
    private void loadConfig(Context ctx) {
        SharedPreferences p = prefs(ctx);
        int  showSec = p.getInt(KEY_SHOW_SEC, DEF_SHOW_SEC);
        float fadeSec = p.getFloat(KEY_FADE_SEC, DEF_FADE_SEC);
        shuffle = p.getBoolean(KEY_SHUFFLE, false);

        showMs = Math.max(200, showSec * 1000);
        fadeMs = Math.max(0, Math.round(fadeSec * 1000f));
    }

    private void loadImages(Context ctx) {
        uris.clear();

        // 1) AlbumStore.getImages(Context)를 우선 시도(반환 타입이 Uri/String 어느 쪽이든 흡수)
        try {
            List<?> list = AlbumStore.getImages(ctx); // List<Uri> 또는 List<String> 가정
            if (list != null) {
                for (Object o : list) {
                    if (o instanceof Uri) {
                        uris.add((Uri) o);
                    } else if (o instanceof String) {
                        try { uris.add(Uri.parse((String) o)); } catch (Throwable ignore) {}
                    }
                }
            }
        } catch (Throwable ignore) { /* 없음 또는 런타임 이슈면 패스 */ }

        // 2) 비어 있으면 prefs CSV 로드
        if (uris.isEmpty()) {
            String csv = prefs(ctx).getString(KEY_IMAGES, "");
            if (csv != null && !csv.isEmpty()) {
                String[] parts = csv.split("\\|\\|");
                for (String s : parts) {
                    if (!s.isEmpty()) {
                        try { uris.add(Uri.parse(s)); } catch (Throwable ignore) {}
                    }
                }
            }
        }

        // 3) 셔플이면 순서 섞기
        if (shuffle && uris.size() > 1) {
            long seed = System.currentTimeMillis();
            Collections.shuffle(uris, new Random(seed));
        }
    }

    private SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}