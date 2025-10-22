package com.neovision.onframe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

/**
 * 설정 루트 화면.
 * - 버튼을 누르면 "내부(ChildFragmentManager)"로 하위 화면을 띄움
 * - 하위 화면이 열려 있을 때는 ViewPager2 좌우 스와이프를 잠금
 * - 뒤로가기 시 하위 화면을 닫고 루트 메뉴로 복귀
 */
public class SettingsFragment extends Fragment {

    private ViewGroup menuContainer;   // 루트 메뉴 영역(버튼들)
    private View subContainer;         // 하위화면 컨테이너(FrameLayout)

    private final FragmentManager.OnBackStackChangedListener backStackListener =
            this::syncUiWithBackstack;

    private OnBackPressedCallback backPressedCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inf.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        menuContainer = v.findViewById(R.id.settings_menu);
        subContainer  = v.findViewById(R.id.sub_container);

        // 버튼 클릭 → 내부 컨테이너에 하위 프래그먼트 띄우기
        v.findViewById(R.id.btn_album_settings).setOnClickListener(view ->
                openSub(new AlbumSettingsFragment(), "album_settings"));

        v.findViewById(R.id.btn_screen_settings).setOnClickListener(view ->
                openSub(new ScreenSettingsFragment(), "screen_settings"));

        // BackStack 변화에 따라 UI 동기화 + 스와이프 잠금/해제
        getChildFragmentManager().addOnBackStackChangedListener(backStackListener);
        syncUiWithBackstack();

        // 시스템 뒤로가기 대응(하위화면 있을 때는 popBackStack)
        backPressedCallback = new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                FragmentManager fm = getChildFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                } else {
                    // 더 이상 하위 스택 없으면 콜백 해제 후 Activity에 위임
                    setEnabled(false);
                    requireActivity().onBackPressed();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);
    }

    private void openSub(@NonNull Fragment fragment, @NonNull String tag) {
        getChildFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.sub_container, fragment, tag)
                .addToBackStack(tag)
                .commit();
    }

    private void syncUiWithBackstack() {
        boolean hasSub = getChildFragmentManager().getBackStackEntryCount() > 0;
        // 메뉴/서브 컨테이너 토글
        menuContainer.setVisibility(hasSub ? View.GONE : View.VISIBLE);
        subContainer.setVisibility(hasSub ? View.VISIBLE : View.GONE);

        // 하위 화면이 열려 있으면 좌우 스와이프 잠금
        if (getActivity() instanceof MainActivity) {
            if (hasSub) ((MainActivity) getActivity()).lockSwipe();
            else        ((MainActivity) getActivity()).unlockSwipe();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getChildFragmentManager().removeOnBackStackChangedListener(backStackListener);
        backPressedCallback = null;
    }
}