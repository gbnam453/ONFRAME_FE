package com.neovision.onframe;
public enum Screen {
    DASHBOARD, ALBUM, SETTINGS;
    public String titleKo() {
        switch (this) {
            case DASHBOARD: return "대시보드";
            case ALBUM:     return "앨범";
            default:        return "설정";
        }
    }
}