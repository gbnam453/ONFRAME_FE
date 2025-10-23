// app/src/main/java/com/neovision/onframe/AlbumSettingsFragment.java
package com.neovision.onframe;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 앨범설정 화면
 * - 네가 준 fragment_album_settings.xml 레이아웃을 그대로 사용
 * - 표시시간(초, 1~60), 페이드(0.0~5.0, 0.1단위), 셔플, 이미지 추가/삭제/순서변경(길게 눌러 드래그)
 * - 저장 버튼을 눌러야 실제로 AlbumStore에 반영
 */
public class AlbumSettingsFragment extends Fragment {

    // 헤더
    private ImageButton btnBack; // 레이아웃은 ImageButton/버튼 혼용 가능하지만 타입은 안전하게 ImageButton로 둠
    private Button btnAdd;
    private Button btnSave;

    // 컨트롤
    private TextView txtShow;   // "한 장 표시: 5초"
    private TextView txtFade;   // "페이드: 1.0초"
    private TextView txtShuffle; // "사용자 순서대로" / "무작위 재생"
    private SeekBar seekShow;
    private SeekBar seekFade;
    private Switch switchShuffle;

    // 목록
    private RecyclerView rv;
    private View emptyHint;
    private ImageListAdapter adapter;
    private final ArrayList<Uri> data = new ArrayList<>();

    // 저장 전 임시값
    private int pendingShowSec;
    private float pendingFadeSec;
    private boolean pendingShuffle;

    private boolean dirty = false;

    private ActivityResultLauncher<String[]> pickImagesLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 이미지 다중 선택 런처
        pickImagesLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (uris == null || uris.isEmpty()) return;

                    // 영구 읽기 권한
                    for (Uri u : uris) {
                        if (u == null) continue;
                        try {
                            requireContext().getContentResolver().takePersistableUriPermission(
                                    u, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (SecurityException ignore) {}
                    }
                    // 로컬 리스트에만 추가 (저장 눌러야 반영)
                    boolean changed = false;
                    for (Uri u : uris) {
                        if (u == null) continue;
                        if (!data.contains(u)) {
                            data.add(u);
                            changed = true;
                        }
                    }
                    if (changed) {
                        adapter.notifyDataSetChanged();
                        updateEmpty();
                        setDirty(true);
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inf.inflate(R.layout.fragment_album_settings, container, false);

        // --- 헤더 ---
        btnBack = v.findViewById(R.id.btn_back);
        btnAdd  = v.findViewById(R.id.btn_add);
        btnSave = v.findViewById(R.id.btn_save);

        ((TextView) v.findViewById(R.id.txt_title)).setText("앨범 설정");

        btnBack.setOnClickListener(view -> safeBack());
        btnAdd.setOnClickListener(view -> pickImagesLauncher.launch(new String[]{"image/*"}));
        btnSave.setOnClickListener(view -> {
            // 저장 버튼을 눌러야 실제 반영
            AlbumStore.setShowSeconds(requireContext(), pendingShowSec);
            AlbumStore.setFadeSeconds(requireContext(), pendingFadeSec);
            AlbumStore.setShuffle(requireContext(), pendingShuffle);
            AlbumStore.setOrder(requireContext(), data);
            setDirty(false);
            safeBack();
        });
        applySaveEnabled();

        // --- 컨트롤 ---
        txtShow = v.findViewById(R.id.txt_show);
        txtFade = v.findViewById(R.id.txt_fade);
        txtShuffle = v.findViewById(R.id.txt_shuffle);
        seekShow = v.findViewById(R.id.seek_show);
        seekFade = v.findViewById(R.id.seek_fade);
        switchShuffle = v.findViewById(R.id.switch_shuffle);

        // 저장된 값 → pending으로
        pendingShowSec = AlbumStore.getShowSeconds(requireContext());
        pendingFadeSec = AlbumStore.getFadeSeconds(requireContext());
        pendingShuffle = AlbumStore.isShuffle(requireContext());

        // 표시시간: 1~60초
        seekShow.setMax(60);
        seekShow.setProgress(Math.max(1, pendingShowSec));
        txtShow.setText("한 장 표시: " + pendingShowSec + "초");
        seekShow.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int sec = Math.max(1, progress);
                pendingShowSec = sec;
                txtShow.setText("한 장 표시: " + sec + "초");
                setDirtyIfChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 페이드: 0.0~5.0s (0.1단위)
        seekFade.setMax(50);
        seekFade.setProgress(Math.round(pendingFadeSec * 10f));
        txtFade.setText(String.format("페이드: %.1f초", pendingFadeSec));
        seekFade.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float sec = progress / 10f;
                pendingFadeSec = sec;
                txtFade.setText(String.format("페이드: %.1f초", sec));
                setDirtyIfChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 셔플
        switchShuffle.setChecked(pendingShuffle);
        txtShuffle.setText(pendingShuffle ? "무작위 재생" : "사용자 순서대로");
        switchShuffle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            pendingShuffle = isChecked;
            txtShuffle.setText(isChecked ? "무작위 재생" : "사용자 순서대로");
            setDirtyIfChanged();
        });

        // --- 리스트 ---
        rv = v.findViewById(R.id.rv_images);
        emptyHint = v.findViewById(R.id.empty_hint);
        rv.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false));
        adapter = new ImageListAdapter(data, () -> {
            setDirty(true);
            updateEmpty();
        });
        rv.setAdapter(adapter);

        ItemTouchHelper touch = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
                Collections.swap(data, from, to);
                adapter.notifyItemMoved(from, to);
                setDirty(true);
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
            @Override public boolean isLongPressDragEnabled() { return true; }
        });
        touch.attachToRecyclerView(rv);

        // 저장된 이미지 → 로컬 리스트로 (저장 누르기 전까지는 메모리에서만 편집)
        data.clear();
        data.addAll(AlbumStore.getImages(requireContext()));
        adapter.notifyDataSetChanged();
        updateEmpty();
        setDirty(false);

        return v;
    }

    private void updateEmpty() {
        if (emptyHint != null) emptyHint.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setDirtyIfChanged() {
        boolean changed = false;
        if (pendingShowSec != AlbumStore.getShowSeconds(requireContext())) changed = true;
        if (Math.abs(pendingFadeSec - AlbumStore.getFadeSeconds(requireContext())) > 0.0001f) changed = true;
        if (pendingShuffle != AlbumStore.isShuffle(requireContext())) changed = true;
        if (!changed) {
            // 리스트 비교
            List<Uri> saved = AlbumStore.getImages(requireContext());
            if (saved.size() != data.size()) changed = true;
            else {
                for (int i = 0; i < saved.size(); i++) {
                    if (!saved.get(i).toString().equals(data.get(i).toString())) { changed = true; break; }
                }
            }
        }
        setDirty(changed);
    }

    private void setDirty(boolean d) {
        dirty = d;
        applySaveEnabled();
    }

    private void applySaveEnabled() {
        if (btnSave != null) {
            btnSave.setEnabled(dirty);
            btnSave.setAlpha(dirty ? 1f : 0.4f);
        }
    }

    private void safeBack() {
        if (!isAdded()) return;
        Fragment parent = getParentFragment();
        if (parent != null) {
            FragmentManager child = parent.getChildFragmentManager();
            if (!child.isStateSaved() && child.getBackStackEntryCount() > 0) {
                child.popBackStack();
                return;
            }
        }
        FragmentManager fm = getParentFragmentManager();
        if (!fm.isStateSaved() && fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
            return;
        }
        if (getActivity() != null) getActivity().getOnBackPressedDispatcher().onBackPressed();
    }

    // --- 어댑터 ---
    private static class ImageListAdapter extends RecyclerView.Adapter<ImageListAdapter.VH> {
        private final ArrayList<Uri> items;
        private final Runnable onListChanged;

        ImageListAdapter(ArrayList<Uri> items, Runnable onListChanged) {
            setHasStableIds(true);
            this.items = items;
            this.onListChanged = onListChanged;
        }

        @Override public long getItemId(int position) {
            return items.get(position).toString().hashCode();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_album_image, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Uri u = items.get(position);

            // 썸네일
            Glide.with(h.thumb.getContext())
                    .load(u)
                    .centerCrop()
                    .into(h.thumb);

            // 파일명
            String last = u.getLastPathSegment();
            h.name.setText(last != null ? last : u.toString());

            // 삭제 (저장 전에 로컬 리스트만 수정)
            h.btnDelete.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    items.remove(pos);
                    notifyItemRemoved(pos);
                    if (onListChanged != null) onListChanged.run();
                }
            });
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final android.widget.ImageView thumb;
            final android.widget.TextView name;
            final android.widget.ImageButton btnDelete;
            VH(@NonNull View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.thumb);
                name = itemView.findViewById(R.id.txt_name);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }
}