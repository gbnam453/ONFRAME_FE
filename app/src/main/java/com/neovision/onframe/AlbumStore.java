package com.neovision.onframe;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class AlbumStore {
    private static final String PREF = "album_store_prefs";
    private static final String KEY_IMAGES = "images"; // JSON 배열 문자열
    private static final String KEY_FADE = "fade_sec";

    public static List<Uri> getImages(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_IMAGES, "[]");
        List<Uri> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                out.add(Uri.parse(arr.getString(i)));
            }
        } catch (Throwable ignored) {}
        return out;
    }

    public static void setImages(Context c, List<Uri> uris) {
        JSONArray arr = new JSONArray();
        for (Uri u : uris) arr.put(u.toString());
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY_IMAGES, arr.toString()).apply();
    }

    public static int getFadeSec(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_FADE, 0);
    }

    public static void setFadeSec(Context c, int sec) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(KEY_FADE, sec).apply();
    }
}