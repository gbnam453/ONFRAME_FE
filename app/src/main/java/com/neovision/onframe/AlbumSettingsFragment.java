package com.neovision.onframe;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.*;

import com.bumptech.glide.Glide;

import java.util.*;

public class AlbumSettingsFragment extends Fragment {

    private final List<Uri> data = new ArrayList<>();
    private AlbumImageAdapter adapter;
    private TextView txtFade;
    private SeekBar seekFade;

    private ActivityResultLauncher<String[]> picker;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inf.inflate(R.layout.fragment_album_settings, container, false);

        // 공용 헤더 제목
        ((TextView) v.findViewById(R.id.txt_title)).setText("앨범 설정");

        // 뒤로가기 안전 처리
        ImageButton btnBack = v.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v1 -> safeNavigateBack());

        RecyclerView rv = v.findViewById(R.id.rv_images);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 3));

        data.clear();
        data.addAll(AlbumStore.getImages(requireContext()));
        adapter = new AlbumImageAdapter(data, pos -> {
            if (pos < 0 || pos >= data.size()) return;
            data.remove(pos);
            adapter.notifyItemRemoved(pos);
            AlbumStore.setImages(requireContext(), data);
        });
        rv.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP|ItemTouchHelper.DOWN|ItemTouchHelper.LEFT|ItemTouchHelper.RIGHT, 0) {
            @Override public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                int f = from.getBindingAdapterPosition();
                int t = to.getBindingAdapterPosition();
                if (f == RecyclerView.NO_POSITION || t == RecyclerView.NO_POSITION) return false;
                Collections.swap(data, f, t);
                adapter.notifyItemMoved(f, t);
                AlbumStore.setImages(requireContext(), data);
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }
            @Override public boolean isLongPressDragEnabled() { return true; }
        });
        helper.attachToRecyclerView(rv);

        Button btnAdd = v.findViewById(R.id.btn_add);
        picker = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            if (uris == null || uris.isEmpty()) return;
            for (Uri u : uris) {
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(
                            u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Throwable ignored) {}
            }
            data.addAll(uris);
            adapter.notifyDataSetChanged();
            AlbumStore.setImages(requireContext(), data);
        });
        btnAdd.setOnClickListener(v12 -> picker.launch(new String[]{"image/*"}));

        txtFade = v.findViewById(R.id.txt_fade);
        seekFade = v.findViewById(R.id.seek_fade);
        int fade = AlbumStore.getFadeSec(requireContext());
        txtFade.setText("페이드(초): " + fade);
        seekFade.setProgress(fade);
        seekFade.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                AlbumStore.setFadeSec(requireContext(), progress);
                txtFade.setText("페이드(초): " + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).lockSwipe();
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 해제는 SettingsFragment에서 백스택 변화로 처리하므로 여기선 생략 가능
        // if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).unlockSwipe();
    }

    /** 자식 프래그먼트에서 안전하게 '설정'으로 복귀 */
    private void safeNavigateBack() {
        if (!isAdded()) return;

        Fragment parent = getParentFragment(); // SettingsFragment 예상
        if (parent != null) {
            FragmentManager fm = parent.getChildFragmentManager();
            if (!fm.isStateSaved() && fm.getBackStackEntryCount() > 0) {
                fm.popBackStack();
                return;
            }
        }
        if (getActivity() != null) {
            getActivity().getOnBackPressedDispatcher().onBackPressed();
        }
    }

    // --- Adapter ---
    static class AlbumImageAdapter extends RecyclerView.Adapter<AlbumImageAdapter.VH> {
        interface Listener { void onDelete(int pos); }
        private final List<Uri> data;
        private final Listener listener;

        AlbumImageAdapter(List<Uri> data, Listener l) { this.data = data; this.listener = l; setHasStableIds(true); }
        @Override public long getItemId(int position) { return data.get(position).toString().hashCode(); }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_album_image, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            Uri u = data.get(position);
            Glide.with(h.itemView).load(u).centerCrop().into(h.thumb);
            h.btnDelete.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) listener.onDelete(pos);
            });
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView thumb;
            ImageButton btnDelete;
            VH(@NonNull View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.img_thumb);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }
}