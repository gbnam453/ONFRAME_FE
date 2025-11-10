package com.neovision.onframe;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 스마트홈 대시보드
 * - 1초마다 GET
 * - POST 하면 바로 한번 더 GET
 * - 색상 버튼 누르면 팝업 열림, 팝업 닫기 가능
 */
public class DashboardFragment extends Fragment {

    // 엔드포인트 베이스
    private static final String BASE = "http://192.168.10.100:8080";

    // GET
    private static final String EP_PLUG_STATE  = BASE + "/api/ha/states/switch.aqara_smart_plug";
    private static final String EP_LIGHT_STATE = BASE + "/api/ha/states/light.smart_multicolor_bulb";
    private static final String EP_AIR_STATE   = BASE + "/api/air/latest";

    // POST
    private static final String EP_PLUG_ON   = BASE + "/api/ha/services/switch/turn_on";
    private static final String EP_PLUG_OFF  = BASE + "/api/ha/services/switch/turn_off";
    private static final String EP_LIGHT_ON  = BASE + "/api/ha/services/light/turn_on";
    private static final String EP_LIGHT_OFF = BASE + "/api/ha/services/light/turn_off";

    // UI
    private TextView txtPlugState;
    private TextView txtLightState;
    private TextView txtAirState, txtAirAqi, txtAirTemp, txtAirHum, txtAirPm25;
    private SeekBar  seekBrightness;

    // 팝업 관련
    private View overlayColor;
    private ColorWheelView colorWheel;
    private View presetRed, presetGreen, presetBlue, presetWhite, presetYellow;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean destroyed = false;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;
            fetchAllStates();
            handler.postDelayed(this, 1000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // 플러그
        txtPlugState = v.findViewById(R.id.txt_plug_state);
        Button btnPlugOn  = v.findViewById(R.id.btn_plug_on);
        Button btnPlugOff = v.findViewById(R.id.btn_plug_off);

        // 라이트
        txtLightState = v.findViewById(R.id.txt_light_state);
        seekBrightness = v.findViewById(R.id.seek_brightness_pct);
        Button btnLightOn   = v.findViewById(R.id.btn_light_on);
        Button btnLightOff  = v.findViewById(R.id.btn_light_off);
        Button btnLightColor = v.findViewById(R.id.btn_light_color);

        // 공기질
        txtAirState = v.findViewById(R.id.txt_air_state);
        txtAirAqi   = v.findViewById(R.id.txt_air_aqi);
        txtAirTemp  = v.findViewById(R.id.txt_air_temp);
        txtAirHum   = v.findViewById(R.id.txt_air_hum);
        txtAirPm25  = v.findViewById(R.id.txt_air_pm25);

        // 팝업
        overlayColor = v.findViewById(R.id.color_overlay);
        colorWheel   = v.findViewById(R.id.color_wheel);
        ImageButton btnColorClose = v.findViewById(R.id.btn_color_close);
        presetRed    = v.findViewById(R.id.preset_red);
        presetGreen  = v.findViewById(R.id.preset_green);
        presetBlue   = v.findViewById(R.id.preset_blue);
        presetWhite  = v.findViewById(R.id.preset_white);
        presetYellow = v.findViewById(R.id.preset_yellow);
        Button btnPopupTemp3000 = v.findViewById(R.id.btn_popup_temp_3000);
        Button btnPopupTemp6500 = v.findViewById(R.id.btn_popup_temp_6500);

        ImageButton btnRefreshAll = v.findViewById(R.id.btn_refresh_all);
        btnRefreshAll.setOnClickListener(view -> fetchAllStates());

        // 플러그 버튼
        btnPlugOn.setOnClickListener(view -> {
            JSONObject obj = new JSONObject();
            try { obj.put("entity_id", "switch.aqara_smart_plug"); } catch (JSONException ignored) {}
            postJson(EP_PLUG_ON, obj);
        });
        btnPlugOff.setOnClickListener(view -> {
            JSONObject obj = new JSONObject();
            try { obj.put("entity_id", "switch.aqara_smart_plug"); } catch (JSONException ignored) {}
            postJson(EP_PLUG_OFF, obj);
        });

        // 라이트 on/off
        btnLightOn.setOnClickListener(view -> {
            JSONObject obj = new JSONObject();
            try { obj.put("entity_id", "light.smart_multicolor_bulb"); } catch (JSONException ignored) {}
            postJson(EP_LIGHT_ON, obj);
        });
        btnLightOff.setOnClickListener(view -> {
            JSONObject obj = new JSONObject();
            try { obj.put("entity_id", "light.smart_multicolor_bulb"); } catch (JSONException ignored) {}
            postJson(EP_LIGHT_OFF, obj);
        });

        // 밝기 (0~100%)
        seekBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                JSONObject obj = new JSONObject();
                try {
                    obj.put("entity_id", "light.smart_multicolor_bulb");
                    obj.put("brightness_pct", progress);
                } catch (JSONException ignored) {}
                postJson(EP_LIGHT_ON, obj);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 색상 버튼 → 팝업 열기
        btnLightColor.setOnClickListener(view -> {
            if (overlayColor != null) overlayColor.setVisibility(View.VISIBLE);
        });

        // 팝업 닫기
        btnColorClose.setOnClickListener(view -> {
            if (overlayColor != null) overlayColor.setVisibility(View.GONE);
        });

        // overlay 눌러도 닫기
        overlayColor.setOnClickListener(view -> overlayColor.setVisibility(View.GONE));

        // 컬러 휠 색 선택
        if (colorWheel != null) {
            colorWheel.setOnColorChangeListener(new ColorWheelView.OnColorChangeListener() {
                @Override
                public void onColorChanged(int r, int g, int b) {
                    JSONObject obj = new JSONObject();
                    try {
                        obj.put("entity_id", "light.smart_multicolor_bulb");
                        JSONArray arr = new JSONArray();
                        arr.put(r);
                        arr.put(g);
                        arr.put(b);
                        obj.put("rgb_color", arr);
                    } catch (JSONException ignored) {}
                    postJson(EP_LIGHT_ON, obj);
                }
            });
        }

        // 프리셋 5개
        presetRed.setOnClickListener(view -> sendRgb(255, 0, 0));
        presetGreen.setOnClickListener(view -> sendRgb(0, 255, 0));
        presetBlue.setOnClickListener(view -> sendRgb(0, 0, 255));
        presetWhite.setOnClickListener(view -> sendRgb(255, 255, 255));
        presetYellow.setOnClickListener(view -> sendRgb(255, 255, 0));

        // 색온도
        btnPopupTemp3000.setOnClickListener(view -> sendColorTempKelvin(3000));
        btnPopupTemp6500.setOnClickListener(view -> sendColorTempKelvin(6500));

        // 폴링 시작
        fetchAllStates();
        handler.postDelayed(pollTask, 1000);
    }

    private void sendRgb(int r, int g, int b) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("entity_id", "light.smart_multicolor_bulb");
            JSONArray arr = new JSONArray();
            arr.put(r); arr.put(g); arr.put(b);
            obj.put("rgb_color", arr);
        } catch (JSONException ignored) {}
        postJson(EP_LIGHT_ON, obj);
    }

    private void sendColorTempKelvin(int kelvin) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("entity_id", "light.smart_multicolor_bulb");
            obj.put("color_temp_kelvin", kelvin);
        } catch (JSONException ignored) {}
        postJson(EP_LIGHT_ON, obj);
    }

    private void fetchAllStates() {
        fetchPlugState();
        fetchLightState();
        fetchAirState();
    }

    private void fetchPlugState() {
        new Thread(() -> {
            String json = httpGet(EP_PLUG_STATE);
            if (json == null) return;
            try {
                JSONObject obj = new JSONObject(json);
                final String state = obj.optString("state", "-");
                runOnUi(() -> {
                    if (txtPlugState != null) txtPlugState.setText("상태: " + state);
                });
            } catch (JSONException ignored) {}
        }).start();
    }

    private void fetchLightState() {
        new Thread(() -> {
            String json = httpGet(EP_LIGHT_STATE);
            if (json == null) return;
            try {
                JSONObject obj = new JSONObject(json);
                final String state = obj.optString("state", "-");
                final JSONObject attrs = obj.optJSONObject("attributes");
                final int brightness = attrs != null ? attrs.optInt("brightness", -1) : -1;
                runOnUi(() -> {
                    if (txtLightState != null) txtLightState.setText("상태: " + state);
                    if (brightness >= 0 && seekBrightness != null) {
                        int pct = Math.round(brightness / 255f * 100f);
                        seekBrightness.setProgress(pct);
                    }
                });
            } catch (JSONException ignored) {}
        }).start();
    }

    private void fetchAirState() {
        new Thread(() -> {
            String json = httpGet(EP_AIR_STATE);
            if (json == null) return;
            try {
                JSONObject root = new JSONObject(json);
                JSONObject aq1 = root.optJSONObject("aq1");
                if (aq1 == null) return;
                final double aqi = aq1.optDouble("aqi", 0);
                final double temp = aq1.optDouble("temperature", 0);
                final double hum  = aq1.optDouble("humidity", 0);
                final double pm25 = aq1.optDouble("pm25", 0);
                runOnUi(() -> {
                    if (txtAirState != null) txtAirState.setText("상태: OK");
                    if (txtAirAqi != null) txtAirAqi.setText("AQI: " + aqi);
                    if (txtAirTemp != null) txtAirTemp.setText("Temp: " + String.format("%.2f°C", temp));
                    if (txtAirHum != null) txtAirHum.setText("Humidity: " + String.format("%.2f%%", hum));
                    if (txtAirPm25 != null) txtAirPm25.setText("PM2.5: " + pm25);
                });
            } catch (JSONException ignored) {}
        }).start();
    }

    // ← 여기만 바뀌었다
    private void postJson(String urlStr, JSONObject jsonObj) {
        final String body = (jsonObj != null) ? jsonObj.toString() : "{}";
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] out = body.getBytes("UTF-8");
                conn.setFixedLengthStreamingMode(out.length);
                OutputStream os = new BufferedOutputStream(conn.getOutputStream());
                os.write(out);
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                // 응답 받았으면 즉시 상태 새로고침
                fetchAllStates();
                // 그리고 아주 살짝 뒤에 한 번 더 (백엔드 반영 늦을 때 대비)
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        fetchAllStates();
                    }
                }, 200);

            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void runOnUi(Runnable r) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(r);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        destroyed = true;
        handler.removeCallbacks(pollTask);
    }
}