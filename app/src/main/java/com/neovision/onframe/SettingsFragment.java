package com.neovision.onframe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    private View mainPanel;       // '설정' 타이틀 포함 영역
    private View childContainer;  // 세부 설정 컨테이너

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inf.inflate(R.layout.fragment_settings, container, false);

        mainPanel = v.findViewById(R.id.settings_main_panel);
        childContainer = v.findViewById(R.id.settings_child_container);

        v.findViewById(R.id.btn_screen_settings).setOnClickListener(view -> {
            showChild(new ScreenSettingsFragment());
        });

        v.findViewById(R.id.btn_album_settings).setOnClickListener(view -> {
            showChild(new AlbumSettingsFragment());
        });

        // 자식 backstack 변화에 따라 '설정' 타이틀 영역 표시/숨김
        getChildFragmentManager().addOnBackStackChangedListener(this::syncHeaderVisibility);
        syncHeaderVisibility();

        return v;
    }

    private void showChild(Fragment f) {
        // 세부 화면 진입 시 컨테이너 표시, 부모 '설정' 패널 숨김
        childContainer.setVisibility(View.VISIBLE);
        mainPanel.setVisibility(View.GONE);

        getChildFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.settings_child_container, f)
                .addToBackStack("sub")
                .commit();

        // 세부화면에서 좌우 스와이프 막기(뷰페이저 사용 시)
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).lockSwipe();
        }
    }

    private void syncHeaderVisibility() {
        boolean hasChild = getChildFragmentManager().getBackStackEntryCount() > 0;
        mainPanel.setVisibility(hasChild ? View.GONE : View.VISIBLE);
        childContainer.setVisibility(hasChild ? View.VISIBLE : View.GONE);

        // 자식 없으면 스와이프 다시 허용
        if (!hasChild && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).unlockSwipe();
        }
    }
}