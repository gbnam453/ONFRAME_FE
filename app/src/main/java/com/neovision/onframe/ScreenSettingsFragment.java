package com.neovision.onframe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 화면 설정: 가로 카드(16:9) 캐러셀
 * - 꾹 눌러 좌/우로 드래그해 순서 변경(평소에는 가로 스크롤 완전 비활성화)
 * - 변경사항이 있을 때만 우측 상단 [저장] 활성화
 * - 세부화면 진입 시 MainActivity의 ViewPager 스와이프 잠금, 종료 시 해제
 */
public class ScreenSettingsFragment extends Fragment {

    private RecyclerView rv;
    private Button btnSave;
    private CardAdapter adapter;

    private final List<Screen> original = new ArrayList<>();
    private final List<Screen> data = new ArrayList<>();
    private boolean dirty = false;

    // 카드 동적 사이즈(RecyclerView 실제 폭 기준)
    private int cardWidthPx = -1;
    private int cardHeightPx = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inf.inflate(R.layout.fragment_screen_settings, container, false);

        // 헤더: 제목/뒤로가기/저장
        ((TextView) v.findViewById(R.id.txt_title)).setText("화면 설정");
        ImageButton btnBack = v.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(view -> safeNavigateBack());

        btnSave = v.findViewById(R.id.btn_save);
        btnSave.setOnClickListener(view -> {
            if (dirty) {
                ScreenOrderStore.set(requireContext(), data);
                original.clear();
                original.addAll(data);
                setDirty(false);

                // MainActivity에 즉시 반영 + '설정' 탭 유지
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).refreshOrderStayOnSettings();
                }
            }
            safeNavigateBack();
        });

        // 데이터 준비
        data.clear();
        data.addAll(ScreenOrderStore.get(requireContext()));
        original.clear();
        original.addAll(data);
        setDirty(false); // 처음엔 변경 없음

        // 리사이클러뷰: 가로 캐러셀(스크롤 불가) + 3장 노출
        rv = v.findViewById(R.id.rv_cards);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setLayoutManager(new NoScrollHLinearLayoutManager(requireContext()));
        adapter = new CardAdapter(data, this::onAnyOrderChanged);
        rv.setAdapter(adapter);

        // 카드 간격 & 좌우 패딩
        final int space = dp(16);         // 카드 사이 간격
        final int sidePeek = dp(56);      // 좌/우 가장자리에 살짝 보이는 여백
        rv.addItemDecoration(new SpacesItemDecoration(space, sidePeek));

        // 롱프레스 드래그로 좌/우 이동(순서 변경) — 스와이프 삭제는 비활성화(0)
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int f = from.getBindingAdapterPosition();
                int t = to.getBindingAdapterPosition();
                if (f == RecyclerView.NO_POSITION || t == RecyclerView.NO_POSITION) return false;
                if (f == t) return false;

                Collections.swap(data, f, t);
                adapter.notifyItemMoved(f, t);
                onAnyOrderChanged(); // 변경감지 → 저장 버튼 활성화
                return true;
            }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // 사용 안함(스와이프 삭제 X)
            }

            @Override public boolean isLongPressDragEnabled() { return true; }
        });
        touchHelper.attachToRecyclerView(rv);

        // RecyclerView 폭이 확정된 뒤 카드(16:9) 크기 계산 + 화면에 정확히 3장 보이게
        rv.post(() -> {
            int visibleCount = 3;
            int rvWidth = rv.getWidth();
            int totalSide = sidePeek * 2;
            int totalGaps = space * (visibleCount - 1);
            int available = Math.max(0, rvWidth - totalSide - totalGaps);

            int maxCard = dp(1280); // 초대형 화면에서 과도하게 커지는 것 방지
            cardWidthPx = Math.min(available / visibleCount, maxCard);
            cardHeightPx = Math.round(cardWidthPx * 9f / 16f);
            if (adapter != null) adapter.setCardSize(cardWidthPx, cardHeightPx);

            // 카드 높이 기준으로 상/하 패딩을 동일하게 줘서 세로 중앙 배치
            rv.post(() -> {
                int rvHeight = rv.getHeight();
                View first = rv.getChildCount() > 0 ? rv.getChildAt(0) : null;
                if (rvHeight > 0 && first != null) {
                    int childH = first.getHeight();
                    int pad = Math.max(0, (rvHeight - childH) / 2);
                    rv.setClipToPadding(false);
                    rv.setPadding(rv.getPaddingLeft(), pad, rv.getPaddingRight(), pad);
                    rv.invalidateItemDecorations();
                }
            });
        });

        // 세부화면 진입 시 뷰페이저 스와이프 잠금
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).lockSwipe();

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).unlockSwipe();
    }

    /** 순서가 바뀔 때마다 호출 → 저장 버튼 활성화 제어 */
    private void onAnyOrderChanged() {
        boolean changed = !data.equals(original);
        setDirty(changed);
    }

    private void setDirty(boolean d) {
        dirty = d;
        btnSave.setEnabled(dirty);
        btnSave.setAlpha(dirty ? 1f : 0.4f);
    }

    /** 자식 프래그먼트에서 안전하게 '설정'으로 복귀 */
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

    private int dp(int dp) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(dp * d);
    }

    /** 가로 전용 + 스크롤 불가 레이아웃매니저 */
    private static class NoScrollHLinearLayoutManager extends LinearLayoutManager {
        NoScrollHLinearLayoutManager(@NonNull android.content.Context ctx) {
            super(ctx, RecyclerView.HORIZONTAL, false);
        }
        @Override public boolean canScrollHorizontally() { return false; }
        @Override public boolean canScrollVertically() { return false; }
    }

    // --- Adapter / ViewHolder / Decoration ---

    private static class CardAdapter extends RecyclerView.Adapter<CardAdapter.VH> {
        private final List<Screen> data;
        private final Runnable onOrderChanged;
        private int cardW = -1;
        private int cardH = -1;

        CardAdapter(List<Screen> data, Runnable onOrderChanged) {
            setHasStableIds(true);
            this.data = data;
            this.onOrderChanged = onOrderChanged;
        }

        void setCardSize(int w, int h) {
            this.cardW = w;
            this.cardH = h;
            notifyDataSetChanged();
        }

        @Override public long getItemId(int position) {
            return data.get(position).name().hashCode();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_screen_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Screen s = data.get(position);
            h.title.setText(getKoreanLabel(s));

            if (cardW > 0 && cardH > 0) {
                ViewGroup.LayoutParams lp = h.card.getLayoutParams();
                lp.width = cardW;
                lp.height = cardH;
                h.card.setLayoutParams(lp);
            }
        }

        @Override public int getItemCount() { return data.size(); }

        private String getKoreanLabel(Screen s) {
            switch (s) {
                case DASHBOARD: return "대시보드";
                case ALBUM:     return "앨범";
                case SETTINGS:  return "설정";
            }
            return s.name();
        }

        static class VH extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final AppCompatTextView title;
            VH(@NonNull View itemView) {
                super(itemView);
                card  = (MaterialCardView) itemView;
                title = itemView.findViewById(R.id.txt_label); // item_screen_card.xml과 일치
            }
        }
    }

    private static class SpacesItemDecoration extends RecyclerView.ItemDecoration {
        private final int space;
        private final int sidePeek;
        SpacesItemDecoration(int space, int sidePeek) {
            this.space = space;
            this.sidePeek = sidePeek;
        }
        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int pos = parent.getChildAdapterPosition(view);
            if (pos == RecyclerView.NO_POSITION) return;

            int last = parent.getAdapter() != null ? parent.getAdapter().getItemCount() - 1 : 0;

            if (pos == 0) outRect.left = sidePeek; else outRect.left = space;
            if (pos == last) outRect.right = sidePeek; else outRect.right = space;
        }
    }
}