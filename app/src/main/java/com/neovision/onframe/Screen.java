package com.neovision.onframe;

public enum Screen {
    DASHBOARD("대시보드"),
    ALBUM("앨범"),
    SETTINGS("설정");

    private final String title;
    Screen(String title) { this.title = title; }
    public String getTitle() { return title; }
}