package com.neovision.onframe;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScreenOrderStore {
    private static final String PREF = "screen_order_prefs";
    private static final String KEY = "order"; // CSV

    public static List<Screen> getOrder(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String csv = sp.getString(KEY, null);
        if (csv == null || csv.isEmpty()) return null;
        List<Screen> out = new ArrayList<>();
        for (String s : csv.split(",")) {
            try { out.add(Screen.valueOf(s)); } catch (Throwable ignored) {}
        }
        return out;
    }

    public static void setOrder(Context c, List<Screen> order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i>0) sb.append(",");
            sb.append(order.get(i).name());
        }
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, sb.toString()).apply();
    }

    public static List<Screen> defaultOrder() {
        return Arrays.asList(Screen.DASHBOARD, Screen.ALBUM, Screen.SETTINGS);
    }
}