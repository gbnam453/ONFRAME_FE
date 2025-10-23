// Screen.java
package com.neovision.onframe;

public enum Screen {
    DASHBOARD,
    ALBUM,
    SETTINGS;

    public String getKoreanTitle() {
        switch (this) {
            case DASHBOARD: return "대시보드";
            case ALBUM:     return "앨범";
            case SETTINGS:  return "설정";
            default:        return name();
        }
    }

    // ✅ 기존 어댑터 호환용 별칭 (ScreenOrderAdapter 등)
    public String getTitle() {
        return getKoreanTitle();
    }
}