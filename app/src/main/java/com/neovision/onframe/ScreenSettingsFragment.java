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
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScreenSettingsFragment extends Fragment {

    private final List<Screen> data = new ArrayList<>();
    private ScreenOrderAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inf.inflate(R.layout.fragment_screen_settings, container, false);

        // 상단 Back + Title
        ImageButton btnBack = v.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v1 -> requireActivity().getSupportFragmentManager().popBackStack());

        RecyclerView rv = v.findViewById(R.id.rv_screens);
        rv.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false));

        List<Screen> current = ScreenOrderStore.getOrder(requireContext());
        if (current == null || current.isEmpty()) current = ScreenOrderStore.defaultOrder();
        data.clear(); data.addAll(current);

        adapter = new ScreenOrderAdapter(data);
        rv.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP|ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                int f = from.getBindingAdapterPosition();
                int t = to.getBindingAdapterPosition();
                if (f == RecyclerView.NO_POSITION || t == RecyclerView.NO_POSITION) return false;
                Collections.swap(data, f, t);
                adapter.notifyItemMoved(f, t);
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }
            @Override public boolean isLongPressDragEnabled() { return true; }
        });
        helper.attachToRecyclerView(rv);

        Button btnSave = v.findViewById(R.id.btn_save);
        btnSave.setOnClickListener(v12 -> {
            ScreenOrderStore.setOrder(requireContext(), data);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshOrderStayOnSettings();
            }
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).lockSwipe();
        }
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).unlockSwipe();
        }
    }
}