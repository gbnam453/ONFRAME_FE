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

        disableEdgeGlow(pager);
        reduceDragSensitivity(pager, 8);

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

    /** ▶ 저장된 순서를 다시 읽어와 '어댑터 교체 없이' 반영하고 설정 탭에 머무르게 함 */
    public void refreshOrderStayOnSettings() {
        List<Screen> newOrder = ScreenOrderStore.get(this);
        // 저장도 동기화(혹시 외부에서만 바뀐 경우 대비)
        saveOrder(newOrder);

        if (adapter != null) {
            adapter.setOrder(newOrder); // setAdapter() 금지 — 기존 프래그먼트 유지
        }
        if (pager != null) {
            int idx = newOrder.indexOf(Screen.SETTINGS);
            if (idx >= 0) pager.setCurrentItem(idx, false);
        }
    }

    /** 외부에서 순서를 직접 주는 경우 */
    public void setOrder(List<Screen> newOrder) {
        if (newOrder == null || newOrder.isEmpty()) return;
        Screen current = order.get(pager.getCurrentItem());
        order.clear();
        order.addAll(newOrder);

        saveOrder(order);
        if (adapter != null) adapter.setOrder(order);

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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

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

    private List<Screen> loadSavedOrderOrDefault() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String csv = sp.getString(KEY_ORDER, null);
        if (csv == null || csv.trim().isEmpty()) {
            return new ArrayList<>(Arrays.asList(Screen.DASHBOARD, Screen.ALBUM, Screen.SETTINGS));
        }
        List<Screen> out = new ArrayList<>();
        for (String p : csv.split(",")) {
            try { out.add(Screen.valueOf(p.trim())); } catch (Throwable ignored) {}
        }
        if (out.isEmpty()) out.addAll(Arrays.asList(Screen.DASHBOARD, Screen.ALBUM, Screen.SETTINGS));
        return out;
    }

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

    private static class CompositeTransformer implements ViewPager2.PageTransformer {
        private final ViewPager2.PageTransformer[] transformers;
        CompositeTransformer(ViewPager2.PageTransformer... t) { this.transformers = t; }
        @Override public void transformPage(@NonNull View page, float position) {
            for (ViewPager2.PageTransformer t : transformers) {
                if (t != null) t.transformPage(page, position);
            }
        }
    }

    private static void disableEdgeGlow(ViewPager2 viewPager2) {
        View child = viewPager2.getChildAt(0);
        if (child instanceof RecyclerView) {
            ((RecyclerView) child).setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
    }

    private static void reduceDragSensitivity(ViewPager2 viewPager2, int factor) {
        try {
            Field ff = ViewPager2.class.getDeclaredField("mRecyclerView");
            ff.setAccessible(true);
            RecyclerView recyclerView = (RecyclerView) ff.get(viewPager2);

            Field touchSlopField = RecyclerView.class.getDeclaredField("mTouchSlop");
            touchSlopField.setAccessible(true);
            int touchSlop = (int) touchSlopField.get(recyclerView);
            touchSlopField.set(recyclerView, touchSlop * Math.max(1, factor));
        } catch (Exception ignored) {}
    }

    /** ViewPager2 어댑터 */
    private static class ScreenPagerAdapter extends FragmentStateAdapter {
        private final List<Screen> order = new ArrayList<>();

        ScreenPagerAdapter(@NonNull AppCompatActivity activity, @NonNull List<Screen> order) {
            super(activity);
            this.order.addAll(order);
        }

        @NonNull @Override
        public Fragment createFragment(int position) {
            Screen screen = order.get(position);
            switch (screen) {
                case DASHBOARD: return new DashboardFragment();
                case ALBUM:     return new AlbumFragment();
                case SETTINGS:
                default:        return new SettingsFragment();
            }
        }

        @Override public int getItemCount() { return order.size(); }

        // 안정적인 ID로 프래그먼트 재사용 유지
        @Override public long getItemId(int position) { return order.get(position).name().hashCode(); }
        @Override public boolean containsItem(long itemId) {
            for (Screen s : order) if (s.name().hashCode() == itemId) return true;
            return false;
        }

        void setOrder(@NonNull List<Screen> newOrder) {
            order.clear();
            order.addAll(newOrder);
            notifyDataSetChanged(); // stableIds + containsItem 덕분에 교체 없이 재배치
        }
    }
}