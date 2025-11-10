// AlbumSettingsFragment.java
package com.neovision.onframe;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 앨범 설정 (공용 헤더 사용)
 * - 변경 발생 시 dirty=true → 저장 버튼 활성화
 * - 저장 시 한 번에 반영(AlbumStore 있으면 사용, 없으면 prefs fallback)
 * - 길게 눌러 위/아래 정렬, 추가/삭제 가능
 * - 표시 시간(초), 페이드(0.1초), 셔플
 */
public class AlbumSettingsFragment extends Fragment {

    // ===== Prefs fallback keys =====
    private static final String PREFS = "album_prefs";
    private static final String KEY_IMAGES  = "album_images_csv";  // "uri||uri||..."
    private static final String KEY_SHOW    = "album_show_sec";
    private static final String KEY_FADE    = "album_fade_sec";
    private static final String KEY_SHUFFLE = "album_shuffle";

    // 헤더
    private ImageButton btnBack;
    private Button btnAdd, btnSave;
    private TextView txtTitle;

    // 좌측 컨트롤
    private TextView txtShow, txtFade;
    private SeekBar seekShow, seekFade;
    private Switch switchShuffle;

    // 우측 리스트
    private RecyclerView rvImages;
    private View emptyHint;
    private ImagesAdapter adapter;

    // 데이터(이미지 + 설정값)
    private final ArrayList<Uri> data = new ArrayList<>();
    private final ArrayList<Uri> original = new ArrayList<>();
    private boolean dirty = false;

    private int   currentShowSec;   // 1~60
    private float currentFadeSec;   // 0.0~10.0 (0.1단위)
    private boolean currentShuffle;

    // 시스템 이미지 피커
    private final ActivityResultLauncher<String[]> pickImagesLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(),
                    uris -> {
                        if (uris == null || uris.isEmpty()) return;
                        persistUris(uris);
                        boolean changed = false;
                        for (Uri u : uris) {
                            if (!data.contains(u)) {
                                data.add(u);
                                changed = true;
                            }
                        }
                        if (changed) {
                            setDirty(true);
                            submitList();
                            updateEmpty();
                            persistNow(); // ✅ 추가 직후 즉시 저장
                        }
                    });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inf.inflate(R.layout.fragment_album_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // --- 공용 헤더 ---
        btnBack  = v.findViewById(R.id.btn_back);
        btnAdd   = v.findViewById(R.id.btn_add);
        btnSave  = v.findViewById(R.id.btn_save);
        txtTitle = v.findViewById(R.id.txt_title);

        txtTitle.setText("앨범 설정");
        btnBack.setOnClickListener(view -> safeNavigateBack());

        btnAdd.setVisibility(View.VISIBLE);
        btnSave.setVisibility(View.VISIBLE);
        setDirty(false); // 초기엔 비활성

        btnAdd.setOnClickListener(view -> pickImagesLauncher.launch(new String[]{"image/*"}));
        btnSave.setOnClickListener(view -> {
            // 한 번에 저장 (설정값들)
            saveImagesCompat(requireContext(), data);
            saveShowCompat(requireContext(), currentShowSec);
            saveFadeCompat(requireContext(), currentFadeSec);
            saveShuffleCompat(requireContext(), currentShuffle);

            original.clear();
            original.addAll(data);
            setDirty(false);
        });

        // --- 좌측 컨트롤 ---
        txtShow = v.findViewById(R.id.txt_show);
        txtFade = v.findViewById(R.id.txt_fade);
        seekShow = v.findViewById(R.id.seek_show);
        seekFade = v.findViewById(R.id.seek_fade);
        switchShuffle = v.findViewById(R.id.switch_shuffle);

        // 초기값 로드
        currentShowSec  = clampShowSec(safeGetShow());
        currentFadeSec  = clampFadeSec(safeGetFade());
        currentShuffle  = safeIsShuffle();

        txtShow.setText("한 장 표시: " + currentShowSec + "초");
        txtFade.setText(String.format(java.util.Locale.KOREA, "페이드: %.1f초", currentFadeSec));

        // 표시 시간: 1~60초
        seekShow.setMax(60);
        seekShow.setProgress(currentShowSec);
        seekShow.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                currentShowSec = clampShowSec(progress <= 0 ? 1 : progress);
                txtShow.setText("한 장 표시: " + currentShowSec + "초");
                setDirty(true);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        // 페이드: 0.0~10.0초 (0.1 단위)
        seekFade.setMax(100);
        seekFade.setProgress((int) Math.round(currentFadeSec * 10f));
        seekFade.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                currentFadeSec = clampFadeSec(progress / 10f);
                txtFade.setText(String.format(java.util.Locale.KOREA, "페이드: %.1f초", currentFadeSec));
                setDirty(true);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        switchShuffle.setChecked(currentShuffle);
        switchShuffle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            currentShuffle = isChecked;
            setDirty(true);
        });

        // --- 우측 리스트 ---
        rvImages = v.findViewById(R.id.rv_images);
        emptyHint = v.findViewById(R.id.empty_hint);

        rvImages.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false));
        adapter = new ImagesAdapter(requireContext(), uri -> {
            int pos = data.indexOf(uri);
            if (pos >= 0) {
                data.remove(pos);
                setDirty(true);
                submitList();
                updateEmpty();
                persistNow(); // ✅ 삭제 직후 즉시 저장
            }
        });
        rvImages.setAdapter(adapter);

        // 길게 눌러 위/아래 드래그 정렬
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            private boolean moved = false;

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                int f = from.getBindingAdapterPosition();
                int t = to.getBindingAdapterPosition();
                if (f == RecyclerView.NO_POSITION || t == RecyclerView.NO_POSITION) return false;
                Collections.swap(data, f, t);
                setDirty(true);
                submitList();
                moved = true;
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }
            @Override public boolean isLongPressDragEnabled() { return true; }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (moved) {
                    persistNow(); // ✅ 드래그 종료 시 한 번만 저장
                    moved = false;
                }
            }
        }).attachToRecyclerView(rvImages);

        // 이미지 로드
        data.clear();
        data.addAll(loadImagesCompat(requireContext()));
        original.clear();
        original.addAll(data);
        submitList();
        updateEmpty();
    }

    // ===== 공용 =====

    private void setDirty(boolean d) {
        dirty = d;
        btnSave.setEnabled(dirty);
        btnSave.setAlpha(dirty ? 1f : 0.4f);
    }

    private void submitList() {
        adapter.submit(new ArrayList<>(data));
    }

    private void updateEmpty() {
        emptyHint.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** 설정 화면으로 안전 복귀 */
    private void safeNavigateBack() {
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

        if (getActivity() != null) {
            getActivity().getOnBackPressedDispatcher().onBackPressed();
        }
    }

    private void persistUris(List<Uri> uris) {
        if (uris == null) return;
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        for (Uri u : uris) {
            try {
                requireContext().getContentResolver().takePersistableUriPermission(u, flags);
            } catch (Throwable ignore) { }
        }
    }

    /** ✅ 이미지 목록 즉시 저장 헬퍼 */
    private void persistNow() {
        saveImagesCompat(requireContext(), data);
    }

    // ===== AlbumStore + Prefs 호환 저장/로드 =====
    // — 이미지 —
    private void saveImagesCompat(Context ctx, List<Uri> list) {
        // 1) AlbumStore.setImages(Context, ArrayList<Uri>) 있으면 반사 호출
        try {
            Method m = AlbumStore.class.getMethod("setImages", Context.class, ArrayList.class);
            ArrayList<Uri> arr = new ArrayList<>(list);
            m.invoke(null, ctx, arr);
            return;
        } catch (Throwable ignore) { /* 없음 → prefs */ }

        // 2) prefs csv 저장
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append("||");
            sb.append(String.valueOf(list.get(i)));
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_IMAGES, sb.toString())
                .apply();
    }

    private List<Uri> loadImagesCompat(Context ctx) {
        ArrayList<Uri> out = new ArrayList<>();
        // 1) AlbumStore.getImages(Context) 우선
        try {
            List<?> raw = AlbumStore.getImages(ctx);
            if (raw != null) {
                for (Object o : raw) {
                    if (o instanceof Uri) out.add((Uri) o);
                    else if (o instanceof String) {
                        try { out.add(Uri.parse((String) o)); } catch (Throwable ignore) {}
                    }
                }
            }
        } catch (Throwable ignore) {}

        if (!out.isEmpty()) return out;

        // 2) prefs csv
        String csv = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_IMAGES, "");
        if (csv != null && !csv.isEmpty()) {
            String[] parts = csv.split("\\|\\|");
            for (String s : parts) {
                if (!s.isEmpty()) {
                    try { out.add(Uri.parse(s)); } catch (Throwable ignore) {}
                }
            }
        }
        return out;
    }

    // — 표시 시간 —
    private void saveShowCompat(Context ctx, int sec) {
        try { AlbumStore.setShowSeconds(ctx, sec); return; }
        catch (Throwable ignore) {}
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_SHOW, sec).apply();
    }
    private int loadShowCompat(Context ctx) {
        try { return AlbumStore.getShowSeconds(ctx); }
        catch (Throwable ignore) {}
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_SHOW, 5);
    }

    // — 페이드 —
    private void saveFadeCompat(Context ctx, float sec) {
        try { AlbumStore.setFadeSeconds(ctx, sec); return; }
        catch (Throwable ignore) {}
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_FADE, sec).apply();
    }
    private float loadFadeCompat(Context ctx) {
        try { return AlbumStore.getFadeSeconds(ctx); }
        catch (Throwable ignore) {}
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getFloat(KEY_FADE, 1.0f);
    }

    // — 셔플 —
    private void saveShuffleCompat(Context ctx, boolean s) {
        try { AlbumStore.setShuffle(ctx, s); return; }
        catch (Throwable ignore) {}
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SHUFFLE, s).apply();
    }
    private boolean loadShuffleCompat(Context ctx) {
        try { return AlbumStore.isShuffle(ctx); }
        catch (Throwable ignore) {}
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SHUFFLE, false);
    }

    // ===== 안전 get + 보정 =====
    private int safeGetShow()    { try { return loadShowCompat(requireContext()); } catch (Throwable e){ return 5; } }
    private float safeGetFade()  { try { return loadFadeCompat(requireContext()); } catch (Throwable e){ return 1.0f; } }
    private boolean safeIsShuffle(){ try { return loadShuffleCompat(requireContext()); } catch (Throwable e){ return false; } }

    private int clampShowSec(int sec) {
        if (sec < 1) sec = 1;
        if (sec > 60) sec = 60;
        return sec;
    }
    private float clampFadeSec(float sec) {
        if (sec < 0f) sec = 0f;
        if (sec > 10f) sec = 10f;
        return sec;
    }

    // ===== 리스트 어댑터 =====
    private static class ImagesAdapter extends RecyclerView.Adapter<ImagesAdapter.VH> {
        interface OnDelete { void onDelete(Uri uri); }

        private final Context ctx;
        private final OnDelete onDelete;
        private final ArrayList<Uri> items = new ArrayList<>();

        ImagesAdapter(Context ctx, OnDelete onDelete) {
            this.ctx = ctx.getApplicationContext();
            this.onDelete = onDelete;
            setHasStableIds(true);
        }

        void submit(ArrayList<Uri> list) {
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return items.size(); }
                @Override public int getNewListSize() { return list.size(); }
                @Override public boolean areItemsTheSame(int o, int n) {
                    return String.valueOf(items.get(o)).equals(String.valueOf(list.get(n)));
                }
                @Override public boolean areContentsTheSame(int o, int n) {
                    return areItemsTheSame(o, n);
                }
            });
            items.clear();
            items.addAll(list);
            diff.dispatchUpdatesTo(this);
        }

        @Override public long getItemId(int position) {
            return String.valueOf(items.get(position)).hashCode();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_album_image, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Uri uri = items.get(position);

            Glide.with(h.thumb.getContext())
                    .load(uri)
                    .centerCrop()
                    .into(h.thumb);

            h.name.setText(getDisplayName(h.itemView.getContext(), uri));

            h.btnDelete.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && onDelete != null) {
                    onDelete.onDelete(items.get(pos));
                }
            });
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView name;
            final View btnDelete;
            VH(@NonNull View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.thumb);
                name  = itemView.findViewById(R.id.txt_name);
                View del = itemView.findViewById(R.id.btn_delete);
                btnDelete = del != null ? del : itemView;
            }
        }

        private static String getDisplayName(Context ctx, Uri uri) {
            try (android.database.Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) return c.getString(idx);
                }
            } catch (Throwable ignore) { }
            String s = String.valueOf(uri);
            int slash = s.lastIndexOf('/');
            return slash >= 0 ? s.substring(slash + 1) : s;
        }
    }
}