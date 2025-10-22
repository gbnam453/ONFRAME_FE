package com.neovision.onframe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
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

        // ← 뒤로가기
        ImageButton btnBack = v.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(view -> {
            FragmentManager fm = getParentFragmentManager();
            if (fm != null && !fm.isStateSaved()) {
                fm.popBackStack();
            }
        });

        // 목록
        RecyclerView rv = v.findViewById(R.id.rv_order);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<Screen> data = ScreenOrderStore.get(requireContext());
        adapter = new ScreenOrderAdapter(data, vh -> {
            if (touchHelper != null) touchHelper.startDrag(vh);
        });
        rv.setAdapter(adapter);

        // 드래그 정렬(스와이프 삭제 X)
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

        // 저장
        Button btnSave = v.findViewById(R.id.btn_save);
        btnSave.setOnClickListener(view -> {
            // 필요한 참조를 "먼저" 확보
            final FragmentActivity act = getActivity();
            final FragmentManager fm = getParentFragmentManager();

            // 1) 순서 저장
            ScreenOrderStore.set(requireContext(), adapter.getData());

            // 2) 먼저 뒤로가기(pop) — 이 프래그먼트는 여기서 분리될 수 있음
            if (fm != null && !fm.isStateSaved()) {
                fm.popBackStack();
            } else if (act != null) {
                // 상태 저장 이슈가 있으면 시스템 뒤로가기로 대체
                act.getOnBackPressedDispatcher().onBackPressed();
            }

            // 3) 분리 이후에는 this.* 사용 금지! — 캐시한 액티비티로 메인 리프레시
            if (act instanceof MainActivity) {
                ((MainActivity) act).refreshOrderStayOnSettings();
            }
        });

        // 세부화면 진입 시 뷰페이저 스와이프 잠금
        FragmentActivity act = getActivity();
        if (act instanceof MainActivity) {
            ((MainActivity) act).lockSwipe();
        }

        return v;
    }

    @Override
    public void onDestroyView() {
        // 세부화면 종료 시 스와이프 해제
        FragmentActivity act = getActivity();
        if (act instanceof MainActivity) {
            ((MainActivity) act).unlockSwipe();
        }
        super.onDestroyView();
    }
}