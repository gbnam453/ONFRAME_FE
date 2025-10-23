package com.neovision.onframe;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 앨범 설정/이미지 URI 저장소 */
public final class AlbumStore {
    private AlbumStore() {}

    private static final String PREFS = "album_store";
    private static final String KEY_SHOW_SEC = "show_seconds";          // int(초) – default 5
    private static final String KEY_FADE_TENTHS = "fade_tenths";        // 0.1초 단위 int – default 5(=0.5s)
    private static final String KEY_SHUFFLE = "shuffle";                // boolean – default false
    private static final String KEY_IMAGES = "images_csv";              // "uri||uri||..."

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // --- 시간/셔플 ---
    public static int getShowSeconds(Context c) {
        return p(c).getInt(KEY_SHOW_SEC, 5);
    }

    public static void setShowSeconds(Context c, int sec) {
        p(c).edit().putInt(KEY_SHOW_SEC, Math.max(1, sec)).apply();
    }

    public static float getFadeSeconds(Context c) {
        int tenths = p(c).getInt(KEY_FADE_TENTHS, 5); // 0.5s
        return tenths / 10f;
    }

    public static void setFadeSeconds(Context c, float sec) {
        // 0.1 단위 보관
        int tenths = Math.max(0, Math.round(sec * 10f));
        p(c).edit().putInt(KEY_FADE_TENTHS, tenths).apply();
    }

    public static boolean isShuffle(Context c) {
        return p(c).getBoolean(KEY_SHUFFLE, false);
    }

    public static void setShuffle(Context c, boolean on) {
        p(c).edit().putBoolean(KEY_SHUFFLE, on).apply();
    }

    // --- 이미지 목록 ---
    public static List<Uri> getImages(Context c) {
        String csv = p(c).getString(KEY_IMAGES, "");
        ArrayList<Uri> out = new ArrayList<>();
        if (csv == null || csv.isEmpty()) return out;
        String[] parts = csv.split("\\|\\|");
        for (String s : parts) {
            if (s == null || s.isEmpty()) continue;
            out.add(Uri.parse(s));
        }
        return out;
    }

    /** 현재 순서를 그대로 저장 (저장 버튼에서 호출) */
    public static void setOrder(Context c, List<Uri> uris) {
        p(c).edit().putString(KEY_IMAGES, toCsv(uris)).apply();
    }

    /** 추가(중복 제거) 후 저장 */
    public static void addImages(Context c, List<Uri> add) {
        List<Uri> cur = getImages(c);
        Set<String> set = new HashSet<>();
        for (Uri u : cur) set.add(u.toString());
        boolean changed = false;
        for (Uri u : add) {
            if (u == null) continue;
            String s = u.toString();
            if (set.add(s)) {
                cur.add(u);
                changed = true;
            }
        }
        if (changed) setOrder(c, cur);
    }

    public static void removeImage(Context c, Uri u) {
        if (u == null) return;
        List<Uri> cur = getImages(c);
        String target = u.toString();
        for (int i = cur.size() - 1; i >= 0; i--) {
            if (target.equals(cur.get(i).toString())) cur.remove(i);
        }
        setOrder(c, cur);
    }

    private static String toCsv(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Uri u : uris) {
            if (u == null) continue;
            if (sb.length() > 0) sb.append("||");
            sb.append(u.toString());
        }
        return sb.toString();
    }
}