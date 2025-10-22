package com.neovision.onframe;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.ImageView;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import android.net.Uri;
import java.util.List;

public class AlbumFragment extends Fragment {
    private ImageView iv;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int index = 0;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        iv = new ImageView(requireContext());
        iv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return iv;
    }

    @Override public void onResume() {
        super.onResume();
        play();
    }
    @Override public void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
    }

    private void play() {
        List<Uri> images = AlbumStore.getImages(requireContext());
        final int fadeSec = AlbumStore.getFadeSec(requireContext());
        if (images == null || images.isEmpty()) {
            iv.setImageDrawable(null);
            return;
        }
        if (index >= images.size()) index = 0;
        Uri u = images.get(index);

        // 간단한 페이드 인/아웃 (초 단위)
        iv.setAlpha(0f);
        Glide.with(this).load(u).into(iv);
        iv.animate().alpha(1f).setDuration(Math.max(0, fadeSec) * 200L).withEndAction(() -> {
            handler.postDelayed(() -> {
                iv.animate().alpha(0f).setDuration(Math.max(0, fadeSec) * 200L).withEndAction(() -> {
                    index++;
                    play();
                }).start();
            }, 1000); // 다음 이미지까지 머무는 시간(데모용 1s)
        }).start();
    }
}