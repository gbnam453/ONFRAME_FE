package com.neovision.onframe;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

class ScreenOrderAdapter extends RecyclerView.Adapter<ScreenOrderAdapter.VH> {

    interface OnStartDrag { void requestDrag(RecyclerView.ViewHolder vh); }

    private final List<Screen> data;
    private final OnStartDrag dragCb;

    ScreenOrderAdapter(List<Screen> data, OnStartDrag dragCb) {
        this.data = data;
        this.dragCb = dragCb;
        setHasStableIds(true);
    }

    List<Screen> getData() { return data; }

    @Override public long getItemId(int position) {
        return data.get(position).ordinal();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_screen_order, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position) {
        Screen s = data.get(position);
        h.title.setText(s.getTitle());
        h.drag.setOnTouchListener((v, e) -> {
            if (dragCb != null) dragCb.requestDrag(h);
            return false;
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title;
        ImageView drag;
        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.txt_title);
            drag  = itemView.findViewById(R.id.img_drag);
        }
    }
}