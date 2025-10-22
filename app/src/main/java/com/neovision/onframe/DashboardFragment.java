package com.neovision.onframe;

import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;

public class DashboardFragment extends Fragment {
    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        View v = new View(requireContext());
        v.setBackgroundColor(0xFFFFFFFF); // 흰 화면
        return v;
    }
}