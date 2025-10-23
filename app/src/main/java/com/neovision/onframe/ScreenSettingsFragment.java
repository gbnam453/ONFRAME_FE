package com.neovision.onframe;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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

        // ← 뒤로가기 (프래그먼트의 매니저 사용)
        ImageButton btnBack = v.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(view -> getParentFragmentManager().popBackStack());

        // 리스트
        RecyclerView rv = v.findViewById(R.id.rv_order);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<Screen> data = ScreenOrderStore.get(requireContext());
        adapter = new ScreenOrderAdapter(data, vh -> {
            if (touchHelper != null) touchHelper.startDrag(vh);
        });
        rv.setAdapter(adapter);

        // 드래그로 순서 변경 (스와이프 삭제 없음)
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
            // 1) 순서 저장 (Context는 여전히 붙어있을 때 requireContext() 사용)
            ScreenOrderStore.set(requireContext(), adapter.getData());

            // 2) 먼저 현재 프래그먼트를 스택에서 제거 (프래그먼트 매니저 사용)
            getParentFragmentManager().popBackStack();

            // 3) 다음 프레임에 안전하게 ViewPager 순서 갱신 (Activity를 통해 호출)
            final Activity act = getActivity();
            if (act instanceof MainActivity && !act.isFinishing() && !act.isDestroyed()) {
                act.getWindow().getDecorView().post(() ->
                        ((MainActivity) act).refreshOrderStayOnSettings()
                );
            }
        });

        // 세부화면 진입 시 뷰페이저 스와이프 잠금
        Activity act = getActivity();
        if (act instanceof MainActivity) ((MainActivity) act).lockSwipe();

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Activity act = getActivity();
        if (act instanceof MainActivity) ((MainActivity) act).unlockSwipe();
    }
}