package com.neovision.onframe;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AlbumStore {
    private static final String PREFS = "onframe_album";
    private static final String KEY_IMAGES   = "images_json";
    private static final String KEY_SHOW_SEC = "show_sec";
    private static final String KEY_FADE_SEC = "fade_sec";
    private static final String KEY_SHUFFLE  = "shuffle";

    private AlbumStore() {}

    public static List<String> getImages(Context ctx){
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_IMAGES, "[]");
        return fromJson(json);
    }
    public static void setImages(Context ctx, List<String> list){
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_IMAGES, toJson(list))
                .apply();
    }

    public static List<String> addImagesFromUris(Context ctx, List<Uri> uris){
        List<String> cur = getImages(ctx);
        Set<String> set = new HashSet<>(cur);
        for (Uri u : uris) {
            if (u == null) continue;
            try {
                // ✅ READ|WRITE 모두 요청 (단, 제공자가 WRITE를 주지 않을 수도 있음)
                ctx.getContentResolver().takePersistableUriPermission(
                        u, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException ignored) { }
            String s = u.toString();
            if (!set.contains(s)) {
                set.add(s);
                cur.add(s);
            }
        }
        setImages(ctx, cur);
        return cur;
    }

    public static void removeAt(Context ctx, List<String> current, int pos){
        if (pos < 0 || pos >= current.size()) return;
        current.remove(pos);
        setImages(ctx, current);
    }

    public static int getShowSec(Context ctx){
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SHOW_SEC, 5);
    }
    public static void setShowSec(Context ctx, int sec){
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_SHOW_SEC, sec).apply();
    }

    public static int getFadeSec(Context ctx){
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_FADE_SEC, 2);
    }
    public static void setFadeSec(Context ctx, int sec){
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_FADE_SEC, sec).apply();
    }

    public static boolean isShuffle(Context ctx){
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHUFFLE, false);
    }
    public static void setShuffle(Context ctx, boolean v){
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHUFFLE, v).apply();
    }

    private static String toJson(List<String> list){
        JSONArray a = new JSONArray();
        for (String s : list) a.put(s);
        return a.toString();
    }
    private static List<String> fromJson(String json){
        ArrayList<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i=0;i<a.length();i++){
                String s = a.optString(i, null);
                if (s != null) out.add(s);
            }
        } catch (JSONException ignored) {}
        return out;
    }
}