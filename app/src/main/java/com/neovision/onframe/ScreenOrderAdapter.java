package com.neovision.onframe;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ScreenOrderAdapter extends RecyclerView.Adapter<ScreenOrderAdapter.VH> {

    private final List<Screen> data;

    public ScreenOrderAdapter(List<Screen> data) {
        this.data = data;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) {
        return data.get(position).name().hashCode();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_screen_row, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position) {
        Screen s = data.get(position);
        h.title.setText(s.titleKo());
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title;
        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.txt_title);
        }
    }
}