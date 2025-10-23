// app/src/main/java/com/neovision/onframe/ScreenCardsAdapter.java
package com.neovision.onframe;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class ScreenCardsAdapter extends RecyclerView.Adapter<ScreenCardsAdapter.VH> {

    public interface OnStartDrag {
        void onStartDrag(RecyclerView.ViewHolder vh);
    }

    private final List<Screen> data;
    private final OnStartDrag onStartDrag;

    private int cardWidthPx = 0;
    private int cardHeightPx = 0;

    public ScreenCardsAdapter(@NonNull List<Screen> data, @NonNull OnStartDrag onStartDrag) {
        setHasStableIds(true);
        this.data = data;
        this.onStartDrag = onStartDrag;
    }

    /** 바깥에서 카드 크기(픽셀)를 지정해서 3장 보이도록 조절할 때 사용 */
    public void setCardSize(int w, int h) {
        this.cardWidthPx = w;
        this.cardHeightPx = h;
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).name().hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_screen_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final VH h, int position) {
        final Screen s = data.get(position);
        h.label.setText(s.getKoreanTitle()); // "대시보드" / "앨범" / "설정"

        if (cardWidthPx > 0 && cardHeightPx > 0) {
            ViewGroup.LayoutParams lp = h.card.getLayoutParams();
            lp.width = cardWidthPx;
            lp.height = cardHeightPx;
            h.card.setLayoutParams(lp);
        }

        // 길게 눌러 드래그 시작
        h.itemView.setOnLongClickListener(v -> {
            if (onStartDrag != null) onStartDrag.onStartDrag(h);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView        label;

        VH(@NonNull View itemView) {
            super(itemView);
            this.card  = (MaterialCardView) itemView;
            this.label = itemView.findViewById(R.id.txt_label); // ✅ XML과 동일한 id 사용
        }
    }
}