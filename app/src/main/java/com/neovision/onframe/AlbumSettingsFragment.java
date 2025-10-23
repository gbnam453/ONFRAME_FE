package com.neovision.onframe;

import android.content.ClipData;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 앨범 설정 화면
 * - 현재 이미지 목록 표시/삭제
 * - 표시시간/페이드시간 조절
 * - 랜덤/사용자순서 토글
 * - 이미지 추가(문서 피커)
 */
public class AlbumSettingsFragment extends Fragment {

    private RecyclerView rv;
    private ImagesAdapter adapter;
    private final List<String> data = new ArrayList<>();

    private TextView txtShow, txtFade, txtShuffle;
    private SeekBar seekShow, seekFade;
    private View toggleShuffle;

    private final ActivityResultLauncher<String[]> picker =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(),
                    uris -> {
                        if (uris == null || uris.isEmpty()) return;
                        List<String> updated = AlbumStore.addImagesFromUris(requireContext(), uris);
                        data.clear();
                        data.addAll(updated);
                        if (adapter != null) adapter.notifyDataSetChanged();
                    });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle s) {
        View v = inf.inflate(R.layout.fragment_album_settings, container, false);

        // 헤더
        ((TextView) v.findViewById(R.id.txt_title)).setText("앨범 설정");
        ImageButton btnBack = v.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(view -> getParentFragmentManager().popBackStack());

        Button btnSave = v.findViewById(R.id.btn_save);
        btnSave.setOnClickListener(view -> {
            // 이 화면은 설정이 변경 즉시 저장되는 구조라 별도 처리 없음
            getParentFragmentManager().popBackStack();
        });

        // 목록
        rv = v.findViewById(R.id.rv_images);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ImagesAdapter(data, pos -> {
            if (pos < 0 || pos >= data.size()) return;
            data.remove(pos);
            adapter.notifyItemRemoved(pos);
            AlbumStore.setImages(requireContext(), data);
            invalidateEmptyInfo(v);
        });
        rv.setAdapter(adapter);

        // 드래그로 정렬
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
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

        // 버튼: 추가
        v.findViewById(R.id.btn_add).setOnClickListener(view ->
                picker.launch(new String[]{"image/*"}));

        // 설정 바인딩
        txtShow = v.findViewById(R.id.txt_show);
        txtFade = v.findViewById(R.id.txt_fade);
        seekShow = v.findViewById(R.id.seek_show);
        seekFade = v.findViewById(R.id.seek_fade);
        txtShuffle = v.findViewById(R.id.txt_shuffle);
        toggleShuffle = v.findViewById(R.id.row_shuffle);

        // 값 로드
        data.clear();
        data.addAll(AlbumStore.getImages(requireContext()));
        adapter.notifyDataSetChanged();
        invalidateEmptyInfo(v);

        int show = Math.max(1, AlbumStore.getShowSec(requireContext()));
        int fade = Math.max(0, AlbumStore.getFadeSec(requireContext()));
        boolean shuffle = AlbumStore.isShuffle(requireContext());

        txtShow.setText("표시 시간: " + show + "초");
        txtFade.setText("페이드: " + fade + "초");
        txtShuffle.setText(shuffle ? "표시 순서: 랜덤" : "표시 순서: 사용자 지정");

        seekShow.setProgress(show);
        seekFade.setProgress(fade);

        seekShow.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int v1 = Math.max(1, progress);
                AlbumStore.setShowSec(requireContext(), v1);
                txtShow.setText("표시 시간: " + v1 + "초");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        seekFade.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int v1 = Math.max(0, progress);
                AlbumStore.setFadeSec(requireContext(), v1);
                txtFade.setText("페이드: " + v1 + "초");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });

        toggleShuffle.setOnClickListener(view -> {
            boolean cur = AlbumStore.isShuffle(requireContext());
            AlbumStore.setShuffle(requireContext(), !cur);
            txtShuffle.setText(!cur ? "표시 순서: 랜덤" : "표시 순서: 사용자 지정");
        });

        return v;
    }

    private void invalidateEmptyInfo(View root) {
        View empty = root.findViewById(R.id.empty_hint);
        if (empty != null) empty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // --- 어댑터 ---

    static class ImagesAdapter extends RecyclerView.Adapter<ImagesAdapter.VH> {
        interface Listener { void onDelete(int pos); }
        private final List<String> data;
        private final Listener listener;

        ImagesAdapter(List<String> data, Listener l) { this.data = data; this.listener = l; }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_album_image, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            String path = data.get(position);
            Object model = path;
            if (path != null) {
                if (path.startsWith("/")) model = new File(path);
                else if (path.startsWith("content://") || path.startsWith("file://")) model = Uri.parse(path);
            }

            // 썸네일
            if (h.thumb != null) {
                Glide.with(h.itemView).load(model).centerCrop().into(h.thumb);
            }
            // 파일명 표시 (있으면)
            if (h.name != null) {
                String name = path;
                int idx = path != null ? path.lastIndexOf('/') : -1;
                if (idx >= 0 && idx + 1 < path.length()) name = path.substring(idx + 1);
                h.name.setText(name != null ? name : "");
            }
            // 삭제 버튼
            if (h.btnDelete != null) {
                h.btnDelete.setOnClickListener(v -> {
                    if (listener != null) listener.onDelete(h.getBindingAdapterPosition());
                });
            }
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView name;
            final View btnDelete;
            VH(@NonNull View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.img_thumb);
                name = itemView.findViewById(R.id.txt_name);
                View del = itemView.findViewById(R.id.btn_delete);
                btnDelete = del != null ? del : itemView.findViewById(R.id.btn_remove);
            }
        }
    }
}