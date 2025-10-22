package com.neovision.onframe;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public final class ScreenOrderStore {
    private static final String PREF = "onframe_prefs";
    private static final String KEY = "screen_order_v1";

    private ScreenOrderStore() {}

    public static List<Screen> get(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY, null);

        // 기본값: 대시보드-앨범-설정 (요청사항)
        List<Screen> def = new ArrayList<>(Arrays.asList(
                Screen.DASHBOARD, Screen.ALBUM, Screen.SETTINGS
        ));

        if (raw == null || raw.trim().isEmpty()) {
            return def;
        }

        List<Screen> out = new ArrayList<>();
        for (String token : raw.split(",")) {
            try {
                Screen s = Screen.valueOf(token.trim());
                if (!out.contains(s)) out.add(s);
            } catch (Throwable ignored) {}
        }
        // 누락된 항목 보정
        for (Screen s : EnumSet.allOf(Screen.class)) {
            if (!out.contains(s)) out.add(s);
        }
        // 과잉 항목 제거(3개만)
        while (out.size() > 3) out.remove(out.size() - 1);
        return out;
    }

    public static void set(Context c, List<Screen> order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(order.get(i).name());
        }
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, sb.toString())
                .apply();
    }
}