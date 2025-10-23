package com.neovision.onframe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

public class ScreenSettingsFragment extends Fragment {

    private ScreenOrderAdapter adapter;
    private ItemTouchHelper touchHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inf.inflate(R.layout.fragment_screen_settings, container, false);

        // 헤더: 제목/뒤로가기/저장
        ((TextView) v.findViewById(R.id.txt_title)).setText("화면 설정");

        ImageButton btnBack = v.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(view -> safeNavigateBack());

        Button btnSave = v.findViewById(R.id.btn_save);
        btnSave.setOnClickListener(view -> {
            if (adapter != null) {
                // 순서 저장
                ScreenOrderStore.set(requireContext(), adapter.getData());
            }
            // 설정으로 안전 복귀
            safeNavigateBack();
        });

        // 리스트/드래그 정렬
        RecyclerView rv = v.findViewById(R.id.rv_order);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<Screen> data = ScreenOrderStore.get(requireContext());
        adapter = new ScreenOrderAdapter(data, vh -> {
            if (touchHelper != null) touchHelper.startDrag(vh);
        });
        rv.setAdapter(adapter);

        ItemTouchHelper.Callback cb = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView recyclerView,
                                            @NonNull RecyclerView.ViewHolder from,
                                            @NonNull RecyclerView.ViewHolder to) {
                int f = from.getBindingAdapterPosition();
                int t = to.getBindingAdapterPosition();
                if (f == RecyclerView.NO_POSITION || t == RecyclerView.NO_POSITION) return false;
                Collections.swap(adapter.getData(), f, t);
                adapter.notifyItemMoved(f, t);
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }
            @Override public boolean isLongPressDragEnabled() { return true; }
        };
        touchHelper = new ItemTouchHelper(cb);
        touchHelper.attachToRecyclerView(rv);

        // 세부화면 진입 시 뷰페이저 스와이프 잠금(있으면)
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).lockSwipe();

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 스와이프 해제는 SettingsFragment 쪽에서 처리 중이면 중복 방지 위해 생략 가능
        // if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).unlockSwipe();
    }

    /** 자식 프래그먼트에서 안전하게 '설정'으로 복귀 */
    private void safeNavigateBack() {
        // 분리(detached) 상태면 아무것도 하지 않음 (예외 방지)
        if (!isAdded()) return;

        // 1) 부모(SettingsFragment)의 childFragmentManager를 우선 시도
        Fragment parent = getParentFragment();
        if (parent != null) {
            FragmentManager child = parent.getChildFragmentManager();
            if (!child.isStateSaved() && child.getBackStackEntryCount() > 0) {
                child.popBackStack();
                return;
            }
        }

        // 2) 자신의 parentFragmentManager 시도 (parent가 null로 붙어있는 구조 대비)
        FragmentManager fm = getParentFragmentManager();
        if (!fm.isStateSaved() && fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
            return;
        }

        // 3) 마지막 안전망: 액티비티 BackDispatcher 위임
        if (getActivity() != null) {
            getActivity().getOnBackPressedDispatcher().onBackPressed();
        }
    }
}