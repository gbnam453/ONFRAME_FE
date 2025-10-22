package com.neovision.onframe;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.neovision.onframe.databinding.ActivityMainBinding;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 16:9 가로 화면 기준 기본 내비게이션
 * - ViewPager2 + 3개 프래그먼트(DASHBOARD, ALBUM, SETTINGS)
 * - 첫 화면: ALBUM
 * - 서브페이지에서 스와이프 잠금용 lockSwipe()/unlockSwipe() 제공
 * - 전체화면(상태바/내비바 숨김) 몰입형 모드
 * - 화면 순서는 SharedPreferences("onframe_prefs", key "screen_order")에 "DASHBOARD,ALBUM,SETTINGS" 형태로 저장/로드
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "onframe_prefs";
    private static final String KEY_ORDER  = "screen_order";

    private ActivityMainBinding vb;
    private ViewPager2 pager;
    private ScreenPagerAdapter adapter;
    private final List<Screen> order = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        applyImmersiveMode();

        pager = vb.pager;

        // 저장된 순서 불러오기(없으면 기본값)
        order.clear();
        order.addAll(loadSavedOrderOrDefault());

        adapter = new ScreenPagerAdapter(this, order);
        pager.setAdapter(adapter);
        pager.setOffscreenPageLimit(3);

        // 엣지글로우 제거 & 필요 시 드래그 민감도 완화
        disableEdgeGlow(pager);
        reduceDragSensitivity(pager, 8);

        // 페이드 없는(실시간 추종) 가벼운 패럴랙스
        ViewPager2.PageTransformer transformer = (page, position) -> {
            float parallax = position * -0.06f;
            page.setTranslationX(page.getWidth() * parallax);
            page.setAlpha(1f);
            page.setScaleX(1f);
            page.setScaleY(1f);
        };
        pager.setPageTransformer(new CompositeTransformer(
                transformer,
                new MarginPageTransformer(0)
        ));

        // 시작 화면: ALBUM
        int startIndex = Math.max(0, order.indexOf(Screen.ALBUM));
        pager.setCurrentItem(startIndex, false);
    }

    /** ⛳️ ScreenSettingsFragment에서 호출: 저장된 순서를 다시 읽어와 적용. 현재는 '설정' 탭에 머무르게 한다. */
    public void refreshOrderStayOnSettings() {
        List<Screen> saved = loadSavedOrderOrDefault();
        // 현재는 설정 탭에 머무르도록 고정
        Screen stay = Screen.SETTINGS;
        order.clear();
        order.addAll(saved);
        adapter.setOrder(order);

        int newIndex = order.indexOf(stay);
        if (newIndex < 0) newIndex = 0;
        pager.setCurrentItem(newIndex, false);
    }

    /** 외부에서 순서를 직접 주는 경우(예: 프래그먼트가 리스트를 넘겨주는 경우) */
    public void setOrder(List<Screen> newOrder) {
        if (newOrder == null || newOrder.isEmpty()) return;
        Screen current = order.get(pager.getCurrentItem());
        order.clear();
        order.addAll(newOrder);
        adapter.setOrder(order);

        // 저장도 함께
        saveOrder(order);

        int newIndex = order.indexOf(current);
        if (newIndex < 0) newIndex = 0;
        pager.setCurrentItem(newIndex, false);
    }

    /** 서브화면에서 좌우 스와이프 잠금/해제용 */
    public void lockSwipe() {
        if (pager != null) pager.setUserInputEnabled(false);
    }

    public void unlockSwipe() {
        if (pager != null) pager.setUserInputEnabled(true);
    }

    /** 포커스 복귀 시 시스템바 다시 숨김 */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    /** ✅ 전체화면(상태바/내비바 숨김) 몰입형 모드 */
    private void applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    /** 저장된 순서 로드(없으면 기본값) */
    private List<Screen> loadSavedOrderOrDefault() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String csv = sp.getString(KEY_ORDER, null);
        if (csv == null || csv.trim().isEmpty()) {
            return new ArrayList<>(Arrays.asList(Screen.DASHBOARD, Screen.ALBUM, Screen.SETTINGS));
        }
        List<Screen> out = new ArrayList<>();
        String[] parts = csv.split(",");
        for (String p : parts) {
            try {
                out.add(Screen.valueOf(p.trim()));
            } catch (Throwable ignored) {}
        }
        if (out.isEmpty()) {
            out.addAll(Arrays.asList(Screen.DASHBOARD, Screen.ALBUM, Screen.SETTINGS));
        }
        return out;
    }

    /** 순서 저장 */
    private void saveOrder(List<Screen> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(list.get(i).name());
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_ORDER, sb.toString())
                .apply();
    }

    /** 여러 Transformer를 합쳐 쓰기 위한 간단한 컴포지트 */
    private static class CompositeTransformer implements ViewPager2.PageTransformer {
        private final ViewPager2.PageTransformer[] transformers;
        CompositeTransformer(ViewPager2.PageTransformer... t) { this.transformers = t; }
        @Override public void transformPage(@NonNull View page, float position) {
            for (ViewPager2.PageTransformer t : transformers) {
                if (t != null) t.transformPage(page, position);
            }
        }
    }

    /** 좌/우 끝에서 보이는 EdgeGlow(파란/주황) 제거 */
    private static void disableEdgeGlow(ViewPager2 viewPager2) {
        View child = viewPager2.getChildAt(0);
        if (child instanceof RecyclerView) {
            ((RecyclerView) child).setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
    }

    /**
     * 스와이프 민감도 줄이기(원하는 경우만 사용). 값이 클수록 덜 민감.
     * 단말 제조사 커스텀에 따라 무시될 수 있음.
     */
    private static void reduceDragSensitivity(ViewPager2 viewPager2, int factor) {
        try {
            Field ff = ViewPager2.class.getDeclaredField("mRecyclerView");
            ff.setAccessible(true);
            RecyclerView recyclerView = (RecyclerView) ff.get(viewPager2);

            Field touchSlopField = RecyclerView.class.getDeclaredField("mTouchSlop");
            touchSlopField.setAccessible(true);
            int touchSlop = (int) touchSlopField.get(recyclerView);
            touchSlopField.set(recyclerView, touchSlop * Math.max(1, factor));
        } catch (Exception ignored) {
        }
    }

    /** ViewPager2 어댑터: 세 개의 Fragment를 반환 */
    private static class ScreenPagerAdapter extends FragmentStateAdapter {
        private final List<Screen> order = new ArrayList<>();

        ScreenPagerAdapter(@NonNull AppCompatActivity activity, @NonNull List<Screen> order) {
            super(activity);
            this.order.addAll(order);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            Screen screen = order.get(position);
            switch (screen) {
                case DASHBOARD: return new DashboardFragment();
                case ALBUM:     return new AlbumFragment();
                case SETTINGS:
                default:        return new SettingsFragment();
            }
        }

        @Override
        public int getItemCount() { return order.size(); }

        /** 안정적인 아이디 제공 */
        @Override
        public long getItemId(int position) { return order.get(position).name().hashCode(); }

        @Override
        public boolean containsItem(long itemId) {
            for (Screen s : order) if (s.name().hashCode() == itemId) return true;
            return false;
        }

        /** 외부에서 순서 갱신 */
        void setOrder(@NonNull List<Screen> newOrder) {
            order.clear();
            order.addAll(newOrder);
            notifyDataSetChanged();
        }
    }
}