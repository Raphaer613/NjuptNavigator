package com.example.njuptcampusnavigator;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Message;
import android.widget.LinearLayout;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Typeface;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;

import com.baidu.mapapi.bikenavi.BikeNavigateHelper;
import com.baidu.mapapi.bikenavi.adapter.IBEngineInitListener;
import com.baidu.mapapi.bikenavi.adapter.IBRoutePlanListener;
import com.baidu.mapapi.bikenavi.adapter.IBTTSPlayer;
import com.baidu.mapapi.bikenavi.adapter.IBRouteGuidanceListener;
import com.baidu.mapapi.bikenavi.model.BikeRouteDetailInfo;
import com.baidu.mapapi.bikenavi.model.BikeNaviDisplayOption;
import com.baidu.mapapi.bikenavi.model.BikeRouteResult;
import com.baidu.mapapi.bikenavi.model.IBRouteIconInfo;
import com.baidu.mapapi.common.model.RouteInfo;
import com.baidu.mapapi.common.model.StepInfo;
import com.baidu.mapapi.walknavi.model.IWRouteIconInfo;
import com.baidu.mapapi.walknavi.model.WalkSimpleMapInfo;
import com.baidu.mapapi.walknavi.adapter.IWRouteGuidanceListener;
import com.baidu.mapapi.walknavi.model.RouteGuideKind;
import com.baidu.mapapi.walknavi.model.WalkRouteResult;
import com.baidu.mapapi.bikenavi.model.BikeRoutePlanError;
import com.baidu.mapapi.bikenavi.params.BikeNaviLaunchParam;
import com.baidu.mapapi.bikenavi.params.BikeRouteNodeInfo;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.walknavi.adapter.IWEngineInitListener;
import com.baidu.mapapi.walknavi.adapter.IWRoutePlanListener;
import com.baidu.mapapi.walknavi.adapter.IWTTSPlayer;
import com.baidu.mapapi.walknavi.model.WalkRoutePlanError;
import com.baidu.mapapi.walknavi.model.WalkNaviDisplayOption;
import com.baidu.mapapi.walknavi.WalkNavigateHelper;
import com.baidu.mapapi.walknavi.params.WalkRouteNodeInfo;
import com.baidu.mapapi.walknavi.params.WalkNaviLaunchParam;

import java.util.Locale;

public final class NaviGuideActivity extends Activity {
    public static final String EXTRA_START_LATITUDE = "start_latitude";
    public static final String EXTRA_START_LONGITUDE = "start_longitude";
    public static final String EXTRA_END_LATITUDE = "end_latitude";
    public static final String EXTRA_END_LONGITUDE = "end_longitude";
    public static final String EXTRA_CYCLING = "cycling";
    public static final String EXTRA_PLACE_NAME = "place_name";
    private static final String EXTRA_GUIDE_READY = "guide_ready";
    private static final String TAG = "NjuptNavi";

    private boolean cycling;
    private boolean navigationStarted;
    private BikeNavigateHelper bikeHelper;
    private WalkNavigateHelper walkHelper;
    private TextToSpeech textToSpeech;
    private boolean ttsReady;
    private String pendingSpeech;
    private boolean pendingSpeechPreempt;
    private boolean handOffToGuidePage;
    private TextView remainingInfo;
    private DirectionArrowView guideFallbackIcon;
    private TextView guideLabel;
    private TextView guidePrimary;
    private TextView guideSecondary;
    private boolean hasSdkGuideText;
    private boolean hasVoiceGuideText;
    private final ArrayList<StepInfo> plannedSteps = new ArrayList<>();
    private int plannedRouteDistance;
    private int remainingMetres = -1;
    private int lastPlannedSpeechStep = -1;
    private int lastPlannedSpeechStage;
    private View sdkNavigationView;
    private String lastNativePrompt = "";
    private volatile long lastRealtimeGuideAt;
    private String lastSpokenManeuver = "";
    private long lastManeuverSpeechAt;
    private int lastManeuverStage;
    private final AtomicInteger speechSequence = new AtomicInteger();
    private long lastGuideDiagnosticAt;
    private boolean mirroringNativeGuide;
    private final Runnable nativeGuideMirror = new Runnable() {
        @Override public void run() {
            if (!mirroringNativeGuide || sdkNavigationView == null || isFinishing() || isDestroyed()) return;
            mirrorNativeGuideText();
            sdkNavigationView.postDelayed(this, 700);
        }
    };
    private String remainingDistance = "等待更新";
    private String remainingTime = "等待更新";
    private boolean arrived;
    private boolean rerouting;
    // A yaw callback can arrive without a matching completion or guidance callback.
    // Keep route-step fallbacks disabled until fresh SDK guidance is observed.
    private boolean awaitingGuideAfterYaw;
    private long reroutingStartedAt;
    private long lastRemainingUpdateAt;
    private static final long REROUTE_STATUS_TIMEOUT_MS = 12000L;
    private final Runnable rerouteStatusTimeout = () -> {
        if (rerouting && !arrived && !isFinishing() && !isDestroyed()) {
            rerouting = false;
            plannedSteps.clear();
            plannedRouteDistance = 0;
            lastPlannedSpeechStep = -1;
            lastPlannedSpeechStage = 0;
            showGuideStatus("转向提示暂未更新，请按地图路线行进");
            renderRemainingInfo();
            Log.w(TAG, "偏航后未收到完成或新指引回调；停止自定义重规划状态，等待 SDK 新指引");
        }
    };
    private final WalkRemainingGuidanceListener walkRemainingListener = new WalkRemainingGuidanceListener();
    private final BikeRemainingGuidanceListener bikeRemainingListener = new BikeRemainingGuidanceListener();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cycling = getIntent().getBooleanExtra(EXTRA_CYCLING, false);
        showLoading();
        boolean guideReady = getIntent().getBooleanExtra(EXTRA_GUIDE_READY, false);
        if (guideReady) {
            setupTextToSpeech();
        }
        try {
            if (guideReady) {
                startGuidePage();
            } else if (cycling) {
                startBikeNavigation();
            } else {
                startWalkNavigation();
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "导航启动异常", error);
            showFailure("导航组件启动失败，请检查 BWNavi SDK、AK 权限和 assets 资源");
        }
    }

    private void startWalkNavigation() {
        try {
            walkHelper = WalkNavigateHelper.getInstance();
            applyWalkDisplayOption();
            walkHelper.initNaviEngine(this, new IWEngineInitListener() {
                @Override
                public void engineInitSuccess() {
                    walkHelper.setTTsPlayer(new IWTTSPlayer() {
                        @Override
                        public int playTTSText(String text, boolean preempt) {
                            Log.i(TAG, "收到步行导航播报：" + text);
                            speakSdkNavigationText(text, preempt);
                            return 0;
                        }
                    });
                    routeWalk();
                }

                @Override
                public void engineInitFail() {
                    showFailure("步行导航引擎初始化失败，请检查 AK 是否开通步行导航权限");
                }
            });
        } catch (RuntimeException error) {
            Log.e(TAG, "步行导航初始化异常", error);
            showFailure("步行导航初始化异常");
        }
    }

    private void startBikeNavigation() {
        try {
            bikeHelper = BikeNavigateHelper.getInstance();
            bikeHelper.initNaviEngine(this, new IBEngineInitListener() {
                @Override
                public void engineInitSuccess() {
                    bikeHelper.setTTsPlayer(new IBTTSPlayer() {
                        @Override
                        public int playTTSText(String text, boolean preempt) {
                            Log.i(TAG, "收到骑行导航播报：" + text);
                            speakSdkNavigationText(text, preempt);
                            return 0;
                        }
                    });
                    routeBike();
                }

                @Override
                public void engineInitFail() {
                    showFailure("骑行导航引擎初始化失败，请检查 AK 是否开通骑行导航权限");
                }
            });
        } catch (RuntimeException error) {
            Log.e(TAG, "骑行导航初始化异常", error);
            showFailure("骑行导航初始化异常");
        }
    }

    private void routeWalk() {
        LatLng start = startPoint();
        LatLng end = endPoint();
        WalkRouteNodeInfo startNode = new WalkRouteNodeInfo();
        startNode.setLocation(start);
        WalkRouteNodeInfo endNode = new WalkRouteNodeInfo();
        endNode.setLocation(end);
        WalkNaviLaunchParam params = new WalkNaviLaunchParam()
                .startNodeInfo(startNode)
                .endNodeInfo(endNode);
        try {
            walkHelper.routePlanWithRouteNode(params, new IWRoutePlanListener() {
                @Override
                public void onRoutePlanStart() {
                    Log.i(TAG, "步行导航开始算路");
                }

                @Override
                public void onRoutePlanSuccess() {
                    runOnUiThread(() -> openGuidePage());
                }

                @Override
                public void onRoutePlanFail(WalkRoutePlanError error) {
                    Log.e(TAG, "步行导航算路失败，错误码：" + error);
                    showFailure("百度地图未找到可用步行导航路线");
                }
            });
        } catch (RuntimeException error) {
            Log.e(TAG, "步行导航算路异常", error);
            showFailure("步行导航算路异常");
        }
    }

    private void routeBike() {
        LatLng start = startPoint();
        LatLng end = endPoint();
        BikeRouteNodeInfo startNode = new BikeRouteNodeInfo();
        startNode.setLocation(start);
        BikeRouteNodeInfo endNode = new BikeRouteNodeInfo();
        endNode.setLocation(end);
        BikeNaviLaunchParam params = new BikeNaviLaunchParam()
                .startNodeInfo(startNode)
                .endNodeInfo(endNode);
        try {
            bikeHelper.routePlanWithRouteNode(params, new IBRoutePlanListener() {
                @Override
                public void onRoutePlanStart() {
                    Log.i(TAG, "骑行导航开始算路");
                }

                @Override
                public void onRoutePlanSuccess() {
                    runOnUiThread(() -> openGuidePage());
                }

                @Override
                public void onRoutePlanFail(BikeRoutePlanError error) {
                    Log.e(TAG, "骑行导航算路失败，错误码：" + error);
                    showFailure("百度地图未找到可用骑行导航路线");
                }
            });
        } catch (RuntimeException error) {
            Log.e(TAG, "骑行导航算路异常", error);
            showFailure("骑行导航算路异常");
        }
    }

    private LatLng startPoint() {
        return new LatLng(
                getIntent().getDoubleExtra(EXTRA_START_LATITUDE, CampusData.SOUTH_GATE.latitude),
                getIntent().getDoubleExtra(EXTRA_START_LONGITUDE, CampusData.SOUTH_GATE.longitude)
        );
    }

    private LatLng endPoint() {
        return new LatLng(
                getIntent().getDoubleExtra(EXTRA_END_LATITUDE, CampusData.CAMPUS_CENTER.latitude),
                getIntent().getDoubleExtra(EXTRA_END_LONGITUDE, CampusData.CAMPUS_CENTER.longitude)
        );
    }

    private void openGuidePage() {
        handOffToGuidePage = true;
        Intent intent = new Intent(this, NaviGuideActivity.class);
        intent.putExtras(getIntent());
        intent.putExtra(EXTRA_GUIDE_READY, true);
        startActivity(intent);
        finish();
    }

    private void startGuidePage() {
        if (cycling) {
            startBikeGuidePage();
        } else {
            startWalkGuidePage();
        }
    }

    private void startWalkGuidePage() {
        try {
            walkHelper = WalkNavigateHelper.getInstance();
            applyWalkDisplayOption();
            walkHelper.setTTsPlayer(new IWTTSPlayer() {
                @Override
                public int playTTSText(String text, boolean preempt) {
                    Log.i(TAG, "收到步行导航播报：" + text);
                    speakSdkNavigationText(text, preempt);
                    return 0;
                }
            });
            View navigationView = walkHelper.onCreate(this);
            if (navigationView == null) {
                throw new IllegalStateException("WalkNavigateHelper.onCreate 返回空 View");
            }
            showNavigationWithRemainingInfo(navigationView);
            walkHelper.setRouteGuidanceListener(this, walkRemainingListener);
            navigationStarted = true;
            walkHelper.startWalkNavi(this);
        } catch (RuntimeException error) {
            Log.e(TAG, "步行导航页面启动异常", error);
            showFailure("步行导航页面启动失败：" + shortError(error));
        }
    }

    private void applyWalkDisplayOption() {
        if (walkHelper != null) {
            WalkNaviDisplayOption option = new WalkNaviDisplayOption()
                    .showImageToAr(false)
                    .showTopGuideLayout(true)
                    .setNaviTextTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
                    .setAutoChangeNaviMode(false);
            walkHelper.setWalkNaviDisplayOption(option);
        }
    }

    private void startBikeGuidePage() {
        try {
            bikeHelper = BikeNavigateHelper.getInstance();
            bikeHelper.setBikeNaviDisplayOption(new BikeNaviDisplayOption()
                    .showTopGuideLayout(true)
                    .setNaviTextTypeface(Typeface.create("sans-serif", Typeface.NORMAL)));
            bikeHelper.setTTsPlayer(new IBTTSPlayer() {
                @Override
                public int playTTSText(String text, boolean preempt) {
                    Log.i(TAG, "收到骑行导航播报：" + text);
                    speakSdkNavigationText(text, preempt);
                    return 0;
                }
            });
            View navigationView = bikeHelper.onCreate(this);
            if (navigationView == null) {
                throw new IllegalStateException("BikeNavigateHelper.onCreate 返回空 View");
            }
            showNavigationWithRemainingInfo(navigationView);
            bikeHelper.setRouteGuidanceListener(this, bikeRemainingListener);
            navigationStarted = true;
            bikeHelper.startBikeNavi(this);
        } catch (RuntimeException error) {
            Log.e(TAG, "骑行导航页面启动异常", error);
            showFailure("骑行导航页面启动失败：" + shortError(error));
        }
    }

    private void showNavigationWithRemainingInfo(View navigationView) {
        sdkNavigationView = navigationView;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        LinearLayout guideCard = new LinearLayout(this);
        guideCard.setOrientation(LinearLayout.HORIZONTAL);
        guideCard.setGravity(Gravity.CENTER_VERTICAL);
        guideCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(Color.WHITE);
        cardBackground.setCornerRadius(dp(16));
        cardBackground.setStroke(dp(1), Color.rgb(220, 234, 244));
        guideCard.setBackground(cardBackground);
        guideCard.setElevation(dp(3));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        cardParams.setMargins(dp(12), dp(8), dp(12), dp(8));

        FrameLayout iconBackground = new FrameLayout(this);
        GradientDrawable iconSurface = new GradientDrawable();
        iconSurface.setColor(Color.rgb(234, 244, 251));
        iconSurface.setCornerRadius(dp(12));
        iconBackground.setBackground(iconSurface);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        iconParams.setMargins(0, 0, dp(14), 0);
        guideCard.addView(iconBackground, iconParams);
        guideFallbackIcon = new DirectionArrowView();
        FrameLayout.LayoutParams fallbackParams = new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER);
        iconBackground.addView(guideFallbackIcon, fallbackParams);
        LinearLayout guideText = new LinearLayout(this);
        guideText.setOrientation(LinearLayout.VERTICAL);
        guideLabel = new TextView(this);
        guideLabel.setText(cycling ? "骑行导航" : "步行导航");
        guideLabel.setTextColor(Color.rgb(0, 104, 183));
        guideLabel.setTextSize(12);
        guideLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        guideText.addView(guideLabel);
        guidePrimary = new TextView(this);
        guidePrimary.setText("正在获取路线指引…");
        guidePrimary.setTextColor(Color.rgb(11, 61, 99));
        guidePrimary.setTextSize(17);
        guidePrimary.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        guidePrimary.setSingleLine(false);
        guidePrimary.setMaxLines(2);
        guidePrimary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        guideText.addView(guidePrimary);
        guideSecondary = new TextView(this);
        guideSecondary.setTextColor(Color.rgb(102, 120, 138));
        guideSecondary.setTextSize(13);
        guideSecondary.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        guideSecondary.setMaxLines(2);
        guideSecondary.setVisibility(View.GONE);
        guideText.addView(guideSecondary);
        guideCard.addView(guideText, new LinearLayout.LayoutParams(0, -2, 1));
        // The stock SDK top panel remains active and receives the guidance text.
        // Our card covers its appearance while reading the displayed text from that same panel.
        FrameLayout navigationArea = new FrameLayout(this);
        navigationArea.addView(navigationView, new FrameLayout.LayoutParams(-1, -1));
        navigationArea.addView(guideCard, cardParams);
        root.addView(navigationArea, new LinearLayout.LayoutParams(-1, 0, 1));
        remainingInfo = new TextView(this);
        remainingInfo.setTextColor(Color.rgb(11, 61, 99));
        remainingInfo.setBackgroundColor(Color.rgb(234, 244, 251));
        remainingInfo.setTextSize(17);
        remainingInfo.setGravity(Gravity.CENTER);
        remainingInfo.setPadding(dp(16), dp(12), dp(16), dp(12));
        remainingInfo.setMinHeight(dp(64));
        remainingInfo.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        root.addView(remainingInfo, new LinearLayout.LayoutParams(-1, -2));
        renderRemainingInfo();
        setContentView(root);
        root.requestApplyInsets();
        mirroringNativeGuide = true;
        navigationView.post(nativeGuideMirror);
        guideCard.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed() && !arrived && !rerouting
                    && guidePrimary != null
                    && "正在获取路线指引…".contentEquals(guidePrimary.getText())) {
                showGuideStatus("暂无转向提示，请参照地图路线");
            }
        }, 12000);
    }

    private void renderRemainingInfo() {
        if (remainingInfo == null) return;
        if (arrived) {
            remainingInfo.setText("已到达目的地");
        } else if (rerouting) {
            remainingInfo.setText("已偏离路线，正在重新规划…");
        } else if (awaitingGuideAfterYaw) {
            boolean distanceIsFresh = lastRemainingUpdateAt >= reroutingStartedAt
                    && android.os.SystemClock.elapsedRealtime() - lastRemainingUpdateAt < 20000L;
            remainingInfo.setText(distanceIsFresh && !"等待更新".equals(remainingDistance)
                    ? "剩余距离：" + remainingDistance + "\n预计还需：" + remainingTime
                    : "转向提示暂未更新，请参照地图路线");
        } else {
            remainingInfo.setText("剩余距离：" + remainingDistance
                    + "\n预计还需：" + remainingTime);
        }
    }

    private void updateRemainingInfo(CharSequence distance, CharSequence time) {
        // Preserve the SDK's formatted text for display; parse units only for route-step fallback.
        final String distanceText = distance == null ? null : distance.toString().trim();
        final String timeText = time == null ? null : time.toString().trim();
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || arrived) return;
            if (distanceText != null && !distanceText.isEmpty()) remainingDistance = distanceText;
            if (distanceText != null) remainingMetres = parseDistanceMetres(distanceText);
            if (timeText != null && !timeText.isEmpty()) remainingTime = timeText;
            if (distanceText != null && !distanceText.isEmpty()) {
                lastRemainingUpdateAt = android.os.SystemClock.elapsedRealtime();
            }
            renderRemainingInfo();
            if (!rerouting) maybeShowPlannedGuide();
        });
    }

    private void updateRerouting(boolean value) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || arrived) return;
            // Repeated yaw callbacks must not restart the timeout or erase new data.
            if (rerouting == value && !(awaitingGuideAfterYaw && !value)) return;
            // Repeated yaw callbacks after the timeout must not lock the UI again.
            if (value && awaitingGuideAfterYaw && !rerouting) return;
            rerouting = value;
            if (sdkNavigationView != null) sdkNavigationView.removeCallbacks(rerouteStatusTimeout);
            if (value) {
                awaitingGuideAfterYaw = true;
                reroutingStartedAt = android.os.SystemClock.elapsedRealtime();
                if (sdkNavigationView != null) {
                    sdkNavigationView.postDelayed(rerouteStatusTimeout, REROUTE_STATUS_TIMEOUT_MS);
                }
                showGuideStatus("已偏离路线，正在重新规划…");
                renderRemainingInfo();
                Log.i(TAG, "收到偏航回调，等待新路线指引");
                return;
            }
            reroutingStartedAt = 0L;
            awaitingGuideAfterYaw = false;
            plannedSteps.clear();
            plannedRouteDistance = 0;
            lastPlannedSpeechStep = -1;
            lastPlannedSpeechStage = 0;
            showGuideStatus("正在获取新的路线指引…");
            hasSdkGuideText = false;
            hasVoiceGuideText = false;
            lastNativePrompt = "";
            lastRealtimeGuideAt = 0L;
            lastSpokenManeuver = "";
            lastManeuverSpeechAt = 0L;
            lastManeuverStage = 0;
            renderRemainingInfo();
            Log.i(TAG, "重新规划完成，恢复自定义导航界面");
        });
    }

    private void showArrival() {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            arrived = true;
            rerouting = false;
            awaitingGuideAfterYaw = false;
            if (sdkNavigationView != null) sdkNavigationView.removeCallbacks(rerouteStatusTimeout);
            showGuideStatus("已到达目的地");
            renderRemainingInfo();
        });
    }

    private void updateGuideText(CharSequence first, CharSequence second) {
        final String primary = first == null ? "" : first.toString().trim();
        final String secondary = second == null ? "" : second.toString().trim();
        if (primary.isEmpty() && secondary.isEmpty()) return;
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || arrived) return;
            if (rerouting || awaitingGuideAfterYaw) {
                String candidate = primary.isEmpty() ? secondary : primary;
                if (candidate.contains("偏离") || candidate.contains("重新规划")) return;
                // A fresh maneuver is evidence that the SDK resumed guidance even
                // when some SDK versions omit onReRouteComplete().
                rerouting = false;
                awaitingGuideAfterYaw = false;
                reroutingStartedAt = 0L;
                if (sdkNavigationView != null) sdkNavigationView.removeCallbacks(rerouteStatusTimeout);
                plannedSteps.clear();
                plannedRouteDistance = 0;
                lastPlannedSpeechStep = -1;
                lastPlannedSpeechStage = 0;
                renderRemainingInfo();
                Log.i(TAG, "收到新导航提示，恢复偏航后的自定义界面：" + candidate);
            }
            hasSdkGuideText = true;
            lastRealtimeGuideAt = android.os.SystemClock.elapsedRealtime();
            guideLabel.setText(cycling ? "骑行导航" : "步行导航");
            // Baidu often splits “沿道路 / 向东出发” across two callbacks fields.
            String main = primary;
            String detail = secondary;
            if (main.isEmpty()) {
                main = detail;
                detail = "";
            } else if (!detail.isEmpty() && main.length() + detail.length() <= 24
                    && (main.startsWith("沿") || main.startsWith("顺着"))
                    && hasManeuver(detail)) {
                main += detail;
                detail = "";
            }
            renderGuide(main, detail);
            maybeSpeakManeuver(main, detail);
        });
    }

    private void maybeSpeakManeuver(String main, String detail) {
        String prompt = (main + " " + detail).replaceAll("\\s+", " ").trim();
        if (!(prompt.contains("左转") || prompt.contains("右转")
                || prompt.contains("向左") || prompt.contains("向右")
                || prompt.contains("掉头") || prompt.contains("调头")
                || prompt.contains("直行") || prompt.contains("到达"))) {
            return;
        }
        // Speak real-time guidance at a useful distance, without reading a far-away turn early.
        int metres = prompt.contains("米") ? parseDistanceMetres(prompt) : -1;
        if (metres > (cycling ? 250 : 150)) {
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        String key = prompt.replaceAll("[0-9]+(?:\\.[0-9]+)?\\s*(?:米|公里)", "")
                .replaceAll("\\s+", "");
        int stage = metres >= 0 && metres <= (cycling ? 50 : 30) ? 2 : 1;
        if (key.equals(lastSpokenManeuver) && stage == lastManeuverStage
                && now - lastManeuverSpeechAt < 45000L) {
            return;
        }
        lastSpokenManeuver = key;
        lastManeuverStage = stage;
        lastManeuverSpeechAt = now;
        Log.i(TAG, "转向文字触发语音：" + prompt + "，阶段=" + stage);
        speakNavigationText(prompt, true);
    }

    private void mirrorNativeGuideText() {
        // Route guidance callbacks may be unavailable without the SDK's advanced
        // authorization. Its built-in TextViews can still show the ordinary prompt.
        ArrayList<TextView> texts = new ArrayList<>();
        int[] origin = new int[2];
        sdkNavigationView.getLocationOnScreen(origin);
        // The SDK can attach its top panel to the Activity rather than the returned map View.
        collectNativeGuideTexts(getWindow().getDecorView(), origin[1], texts);
        if (texts.isEmpty()) {
            maybeShowPlannedGuide();
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastGuideDiagnosticAt > 10000) {
                lastGuideDiagnosticAt = now;
                Log.i(TAG, "诱导状态：模式=" + (cycling ? "骑行" : "步行")
                        + "，原生提示=0，文字回调=" + hasSdkGuideText
                        + "，语音回调=" + hasVoiceGuideText
                        + "，规划路段=" + plannedSteps.size()
                        + "，剩余米数=" + remainingMetres);
            }
            return;
        }
        texts.sort(Comparator.comparingInt((TextView view) -> {
            int[] position = new int[2];
            view.getLocationOnScreen(position);
            return position[1];
        }).thenComparingInt(view -> {
            int[] position = new int[2];
            view.getLocationOnScreen(position);
            return position[0];
        }));
        String first = texts.get(0).getText().toString().trim();
        String second = texts.size() > 1 ? texts.get(1).getText().toString().trim() : "";
        String prompt = first + "\n" + second;
        if (!prompt.equals(lastNativePrompt)) {
            lastNativePrompt = prompt;
            Log.i(TAG, "读取原生提示：" + prompt.replace('\n', '/'));
            updateGuideText(first, second);
        }
    }

    private void collectNativeGuideTexts(View view, int navigationTop, ArrayList<TextView> result) {
        if (view == guideLabel || view == guidePrimary || view == guideSecondary
                || view == remainingInfo) return;
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            String value = textView.getText().toString().trim();
            int[] position = new int[2];
            textView.getLocationOnScreen(position);
            if (!value.isEmpty() && position[1] >= navigationTop
                    && position[1] - navigationTop < dp(160)
                    && !value.contains("退出") && !value.contains("全览")
                    && !value.contains("路线朝上") && !value.contains("GPS")
                    && (value.startsWith("沿") || value.startsWith("向")
                    || value.startsWith("前方") || value.contains("转")
                    || value.contains("直行") || value.contains("到达")
                    || value.contains("道路") || value.endsWith("路"))) {
                result.add(textView);
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectNativeGuideTexts(group.getChildAt(i), navigationTop, result);
            }
        }
    }

    private int parseDistanceMetres(String value) {
        Matcher number = Pattern.compile("[0-9]+(?:\\.[0-9]+)?").matcher(value.replace(",", ""));
        if (!number.find()) return -1;
        try {
            double metres = Double.parseDouble(number.group());
            if (value.contains("公里") || value.contains("千米")
                    || value.toLowerCase(Locale.ROOT).contains("km")) metres *= 1000;
            return (int) Math.round(metres);
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    private void loadPlannedSteps() {
        if (!plannedSteps.isEmpty()) return;
        try {
            List<RouteInfo> routes = null;
            int reportedDistance = 0;
            if (cycling && bikeHelper != null) {
                BikeRouteResult result = bikeHelper.getBikeNaviRouteInfo();
                if (result != null) {
                    routes = result.getRouteInfos();
                    reportedDistance = result.getDistance();
                }
                if ((routes == null || routes.isEmpty()) && bikeHelper.getBikePlanRoute() != null
                        && !bikeHelper.getBikePlanRoute().isEmpty()) {
                    BikeRouteResult plan = bikeHelper.getBikePlanRoute().get(0);
                    routes = plan.getRouteInfos();
                    reportedDistance = plan.getDistance();
                }
            } else if (walkHelper != null) {
                WalkRouteResult result = walkHelper.getWalkNaviRouteInfo();
                if (result != null) {
                    routes = result.getRouteInfos();
                    reportedDistance = result.getDistance();
                }
                if ((routes == null || routes.isEmpty()) && walkHelper.getWalkPlanRoute() != null
                        && !walkHelper.getWalkPlanRoute().isEmpty()) {
                    WalkRouteResult plan = walkHelper.getWalkPlanRoute().get(0);
                    routes = plan.getRouteInfos();
                    reportedDistance = plan.getDistance();
                }
            }
            if (routes == null) return;
            for (RouteInfo route : routes) {
                if (route == null || route.getStepInfos() == null) continue;
                for (StepInfo step : route.getStepInfos()) {
                    if (step != null && step.getDistance() > 0) {
                        plannedSteps.add(step);
                        plannedRouteDistance += step.getDistance();
                    }
                }
            }
            if (reportedDistance > 0 && Math.abs(plannedRouteDistance - reportedDistance)
                    > Math.max(80, reportedDistance / 4)) {
                Log.w(TAG, "规划路段长度与路线总长不一致：" + plannedRouteDistance
                        + "/" + reportedDistance);
                plannedSteps.clear();
                plannedRouteDistance = 0;
                return;
            }
            if (!plannedSteps.isEmpty()) {
                Log.i(TAG, "读取规划路段：" + plannedSteps.size() + "段，" + plannedRouteDistance + "米");
                for (int i = 0; i < plannedSteps.size(); i++) {
                    StepInfo step = plannedSteps.get(i);
                    Log.i(TAG, "规划路段" + i + "：距离=" + step.getDistance()
                            + "米，方向=" + step.getGuideKind() + "，道路=" + step.getRoadName());
                }
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "读取规划路段失败", error);
        }
    }

    private void maybeShowPlannedGuide() {
        if (arrived || rerouting || awaitingGuideAfterYaw || guidePrimary == null
                || android.os.SystemClock.elapsedRealtime() - lastRealtimeGuideAt < 15000L) return;
        loadPlannedSteps();
        if (plannedSteps.isEmpty() || remainingMetres < 0 || plannedRouteDistance <= 0
                || remainingMetres > plannedRouteDistance + 80) return;
        int travelled = Math.max(0, plannedRouteDistance - remainingMetres);
        int endOfStep = 0;
        for (int i = 0; i < plannedSteps.size(); i++) {
            StepInfo step = plannedSteps.get(i);
            endOfStep += step.getDistance();
            if (travelled >= endOfStep && i < plannedSteps.size() - 1) continue;
            String road = step.getRoadName() == null ? "当前道路" : step.getRoadName().trim();
            if (road.isEmpty()) road = "当前道路";
            String title = "沿" + road + "前行";
            String detail = "路线规划参考";
            String action = "";
            int distanceToTurn = -1;
            if (i + 1 < plannedSteps.size()) {
                StepInfo next = plannedSteps.get(i + 1);
                action = plannedAction(next.getGuideKind() == null ? "" : next.getGuideKind().name());
                if (!action.isEmpty()) {
                    distanceToTurn = Math.max(0, endOfStep - travelled);
                    title = "前方" + distanceToTurn + "米" + action;
                    String nextRoad = next.getRoadName();
                    detail = nextRoad == null || nextRoad.trim().isEmpty()
                            ? "沿规划路线行进" : "进入" + nextRoad.trim();
                }
            }
            guideLabel.setText("路线规划提示");
            renderGuide(title, detail);
            maybeSpeakPlannedTurn(i, action, distanceToTurn, title);
            return;
        }
    }

    private void maybeSpeakPlannedTurn(int stepIndex, String action, int distance, String prompt) {
        if (distance < 0 || !(action.contains("左转") || action.contains("右转")
                || action.contains("掉头"))) return;
        int stage = distance <= (cycling ? 30 : 20) ? 2
                : distance <= (cycling ? 100 : 70) ? 1 : 0;
        if (stage == 0 || stepIndex == lastPlannedSpeechStep
                && stage <= lastPlannedSpeechStage) return;
        lastPlannedSpeechStep = stepIndex;
        lastPlannedSpeechStage = stage;
        Log.i(TAG, "规划路线临近转弯：路段=" + stepIndex + "，距离=" + distance
                + "，方向=" + action + "，阶段=" + stage);
        speakNavigationText(prompt, true);
    }

    private String plannedAction(String kind) {
        if (kind.contains("Dest")) return "到达目的地";
        if (kind.startsWith("NE_WTT_NAV_TURN_LEFT")) return "左转";
        if (kind.startsWith("NE_WTT_NAV_TURN_RIGHT")) return "右转";
        if (kind.startsWith("NE_WTT_NAV_STRAIGHT")) return "直行";
        if (kind.contains("UTurn")) return "掉头";
        if (kind.contains("_Left_PassRoad") || kind.contains("_Goto_Left_Road")) return "左转";
        if (kind.contains("_Right_PassRoad") || kind.contains("_Goto_Right_Road")) return "右转";
        if (kind.endsWith("_Front_Straight")) return "直行";
        if (kind.endsWith("_Right") || kind.endsWith("_Right_Front")
                || kind.endsWith("_Right_Back")) return "右转";
        if (kind.endsWith("_Left") || kind.endsWith("_Left_Front")
                || kind.endsWith("_Left_Back")) return "左转";
        if (kind.endsWith("_Front")) return "直行";
        return "";
    }

    private boolean hasManeuver(String text) {
        return text.contains("转") || text.contains("直行") || text.contains("出发")
                || text.contains("继续") || text.contains("到达");
    }

    private void renderGuide(String main, String detail) {
        if (guidePrimary == null || rerouting) return;
        guidePrimary.setText(main);
        if (detail == null || detail.isEmpty() || detail.equals(main)) {
            guideSecondary.setVisibility(View.GONE);
        } else {
            guideSecondary.setText(detail);
            guideSecondary.setVisibility(View.VISIBLE);
        }
        guideFallbackIcon.setDirection(main + " " + detail);
        guideFallbackIcon.setVisibility(View.VISIBLE);
    }

    private void showGuideStatus(String status) {
        if (guidePrimary == null) return;
        guidePrimary.setText(status);
        guideSecondary.setVisibility(View.GONE);
        guideFallbackIcon.setDirection(status);
        guideFallbackIcon.setVisibility(View.VISIBLE);
    }

    private void updateVoiceGuide(String text) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed() && !arrived && !rerouting
                    && !hasSdkGuideText && guidePrimary != null) {
                hasVoiceGuideText = true;
                guideLabel.setText(cycling ? "骑行导航" : "步行导航");
                renderGuide(text.trim(), "");
            }
        });
    }

    /** A blue maneuver symbol when Baidu does not provide an icon drawable. */
    private final class DirectionArrowView extends View {
        private static final int WAIT = 0;
        private static final int STRAIGHT = 1;
        private static final int LEFT = 2;
        private static final int RIGHT = 3;
        private static final int UTURN = 4;
        private static final int ARRIVE = 5;
        private int direction = WAIT;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DirectionArrowView() {
            super(NaviGuideActivity.this);
            paint.setColor(Color.rgb(0, 104, 183));
            paint.setStrokeWidth(3.2f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setDirection(String instruction) {
            if (instruction.contains("到达")) direction = ARRIVE;
            else if (instruction.contains("掉头") || instruction.contains("调头")) direction = UTURN;
            else if (instruction.contains("左转") || instruction.contains("向左")) direction = LEFT;
            else if (instruction.contains("右转") || instruction.contains("向右")) direction = RIGHT;
            else if (instruction.contains("直行") || instruction.contains("继续")
                    || instruction.contains("出发") || instruction.contains("前行")) direction = STRAIGHT;
            else direction = WAIT;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.scale(getWidth() / 32f, getHeight() / 32f);
            if (direction == WAIT) {
                paint.setStyle(Paint.Style.FILL);
                for (int x = 9; x <= 23; x += 7) canvas.drawCircle(x, 16, 1.8f, paint);
            } else if (direction == ARRIVE) {
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawCircle(16, 13, 7, paint);
                canvas.drawCircle(16, 13, 2, paint);
                canvas.drawLine(16, 20, 16, 27, paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                Path arrow = new Path();
                if (direction == RIGHT || direction == LEFT) {
                    if (direction == LEFT) canvas.scale(-1, 1, 16, 16);
                    arrow.moveTo(8, 26);
                    arrow.lineTo(8, 17);
                    arrow.quadTo(8, 12, 13, 12);
                    arrow.lineTo(25, 12);
                    canvas.drawPath(arrow, paint);
                    canvas.drawLine(20, 7, 25, 12, paint);
                    canvas.drawLine(20, 17, 25, 12, paint);
                } else if (direction == UTURN) {
                    arrow.moveTo(24, 27);
                    arrow.lineTo(24, 14);
                    arrow.cubicTo(24, 3, 9, 3, 9, 14);
                    arrow.lineTo(9, 21);
                    canvas.drawPath(arrow, paint);
                    canvas.drawLine(4, 16, 9, 21, paint);
                    canvas.drawLine(14, 16, 9, 21, paint);
                } else {
                    canvas.drawLine(16, 27, 16, 6, paint);
                    canvas.drawLine(10, 12, 16, 6, paint);
                    canvas.drawLine(22, 12, 16, 6, paint);
                }
            }
            canvas.restore();
        }
    }

    // Separate listeners prevent collisions between SDK default interface methods.
    private final class WalkRemainingGuidanceListener implements IWRouteGuidanceListener {
        @Override public void onRemainDistanceUpdate(CharSequence distance) {
            updateRemainingInfo(distance, null);
        }
        @Override public void onRemainDistanceUpdate(int distanceMetres) {
            if (distanceMetres >= 0) {
                updateRemainingInfo(formatDistance(distanceMetres), null);
            }
        }
        @Override public void onRemainTimeUpdate(CharSequence time) {
            updateRemainingInfo(null, time);
        }
        // New SDK versions also provide remaining seconds as an integer.
        public void onRemainTimeUpdate(int seconds) {
            if (seconds >= 0) {
                String formatted = seconds < 60 ? "不足1分钟"
                        : ((seconds + 59L) / 60L) + "分钟";
                updateRemainingInfo(null, formatted);
            }
        }
        @Override public void onArriveDest() { showArrival(); }
        @Override public void onRouteFarAway(CharSequence text, Drawable icon) {
            updateRerouting(true);
        }
        @Override public void onRoutePlanYawing(CharSequence text, Drawable icon) {
            updateRerouting(true);
        }
        @Override public void onReRouteComplete() { updateRerouting(false); }
        @Override public void onRouteGuideIconUpdate(Drawable icon) { }
        @Override public void onRouteGuideKind(RouteGuideKind kind) { }
        @Override public void onRoadGuideTextUpdate(CharSequence first, CharSequence second) {
            Log.i(TAG, "步行文字回调：" + first + " / " + second);
            updateGuideText(first, second);
        }
        @Override public void onGpsStatusChange(CharSequence text, Drawable icon) { }
        @Override public void onVibrate() { }
        @Override public void onNaviLocationUpdate() { }
        @Override public void onSimpleMapInfoUpdate(WalkSimpleMapInfo info) { }
        @Override public void onRouteGuideIconInfoUpdate(IWRouteIconInfo info) { }
        @Override public void onFinalEnd(Message message) { showArrival(); }
        @Override public void onIndoorEnd(Message message) { }
    }

    private final class BikeRemainingGuidanceListener implements IBRouteGuidanceListener {
        @Override public void onRemainDistanceUpdate(CharSequence distance) {
            updateRemainingInfo(distance, null);
        }
        @Override public void onRemainTimeUpdate(CharSequence time) {
            updateRemainingInfo(null, time);
        }
        // New SDK versions also provide remaining seconds as an integer.
        public void onRemainTimeUpdate(int seconds) {
            if (seconds >= 0) {
                String formatted = seconds < 60 ? "不足1分钟"
                        : ((seconds + 59L) / 60L) + "分钟";
                updateRemainingInfo(null, formatted);
            }
        }
        @Override public void onArriveDest() { showArrival(); }
        @Override public void onRouteFarAway(CharSequence text, Drawable icon) {
            updateRerouting(true);
        }
        @Override public void onRoutePlanYawing(CharSequence text, Drawable icon) {
            updateRerouting(true);
        }
        @Override public void onReRouteComplete() { updateRerouting(false); }
        @Override public void onRouteGuideIconUpdate(Drawable icon) { }
        @Override public void onRouteGuideKind(RouteGuideKind kind) { }
        @Override public void onRoadGuideTextUpdate(CharSequence first, CharSequence second) {
            Log.i(TAG, "骑行文字回调：" + first + " / " + second);
            updateGuideText(first, second);
        }
        @Override public void onGpsStatusChange(CharSequence text, Drawable icon) { }
        @Override public void onVibrate() { }
        @Override public void onRouteGuideIconInfoUpdate(IBRouteIconInfo info) { }
        @Override public void onGetRouteDetailInfo(BikeRouteDetailInfo info) { }
        @Override public void onNaviLocationUpdate() { }
    }

    private String formatDistance(int distanceMetres) {
        if (distanceMetres < 1000) {
            return distanceMetres + "米";
        }
        return String.format(Locale.CHINA, "%.1f公里", distanceMetres / 1000.0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        Log.i(TAG, "系统TTS开始发声：" + id);
                    }
                    @Override public void onDone(String id) {
                        Log.i(TAG, "系统TTS播报完成：" + id);
                    }
                    @Override public void onError(String id) {
                        Log.e(TAG, "系统TTS播报失败：" + id);
                    }
                });
                textToSpeech.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
                textToSpeech.setSpeechRate(1.0f);
                textToSpeech.setPitch(1.0f);
                int result = chooseTtsLanguage();
                ttsReady = isTtsLanguageReady(result);
                Log.i(TAG, "系统TTS初始化完成，engine=" + textToSpeech.getDefaultEngine()
                        + ", languageResult=" + result + ", ready=" + ttsReady);
                if (!ttsReady) {
                    showToast("系统TTS不支持中文播报，请在手机设置中安装或切换语音引擎");
                    return;
                }
                if (ttsReady && pendingSpeech != null) {
                    speakNavigationText(pendingSpeech, pendingSpeechPreempt);
                    pendingSpeech = null;
                } else if (!ttsReady) {
                    Log.w(TAG, "系统语音引擎缺少中文语音数据");
                    runOnUiThread(() -> Toast.makeText(this,
                            "系统语音引擎不支持中文，请安装中文语音包", Toast.LENGTH_LONG).show());
                }
            } else {
                Log.e(TAG, "系统TTS初始化失败，status=" + status);
                showToast("系统TTS初始化失败，请检查手机语音合成引擎");
            }
        });
    }

    private int chooseTtsLanguage() {
        int result = textToSpeech.setLanguage(Locale.CHINA);
        if (isTtsLanguageReady(result)) {
            return result;
        }
        result = textToSpeech.setLanguage(Locale.SIMPLIFIED_CHINESE);
        if (isTtsLanguageReady(result)) {
            return result;
        }
        result = textToSpeech.setLanguage(Locale.CHINESE);
        if (isTtsLanguageReady(result)) {
            return result;
        }
        return textToSpeech.setLanguage(Locale.getDefault());
    }

    private boolean isTtsLanguageReady(int result) {
        return result != TextToSpeech.LANG_MISSING_DATA
                && result != TextToSpeech.LANG_NOT_SUPPORTED;
    }

    private void speakSdkNavigationText(String text, boolean preempt) {
        if (text == null || text.trim().isEmpty()) return;
        lastRealtimeGuideAt = android.os.SystemClock.elapsedRealtime();
        // SDK speech is another source of fresh guidance when the text callback is absent.
        if (hasManeuver(text) && !text.contains("偏离") && !text.contains("重新规划")) {
            runOnUiThread(() -> {
                if (!awaitingGuideAfterYaw || arrived || isFinishing() || isDestroyed()) return;
                rerouting = false;
                awaitingGuideAfterYaw = false;
                reroutingStartedAt = 0L;
                plannedSteps.clear();
                plannedRouteDistance = 0;
                if (sdkNavigationView != null) sdkNavigationView.removeCallbacks(rerouteStatusTimeout);
                renderRemainingInfo();
                Log.i(TAG, "收到 SDK 新语音指引，恢复偏航后的提示：" + text);
            });
        }
        speakNavigationText(text, preempt);
    }

    private void speakNavigationText(String text, boolean preempt) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        Log.d(TAG, "收到导航语音提示：" + text);
        updateVoiceGuide(text);
        if (textToSpeech == null || !ttsReady) {
            Log.w(TAG, "系统TTS未就绪，暂存播报：" + text);
            pendingSpeech = text;
            pendingSpeechPreempt = preempt;
            return;
        }
        Log.i(TAG, "系统TTS开始播报：" + text);
        int result = textToSpeech.speak(
                text,
                preempt ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD,
                null,
                "njupt-navigation-" + speechSequence.incrementAndGet()
        );
        Log.i(TAG, "系统TTS请求结果=" + result + "，内容=" + text);
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "系统语音播报请求失败");
        }
    }

    private void showLoading() {
        FrameLayout root = new FrameLayout(this);
        TextView message = new TextView(this);
        message.setText(cycling ? "正在初始化骑行导航..." : "正在初始化步行导航...");
        message.setTextSize(16);
        message.setGravity(Gravity.CENTER);
        root.addView(message, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        setContentView(root);
    }

    private void showFailure(String message) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> showFailure(message));
            return;
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    private void showToast(String message) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> showToast(message));
            return;
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String shortError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return error.getClass().getSimpleName() + "，" + message;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sdkNavigationView != null && !mirroringNativeGuide) {
            mirroringNativeGuide = true;
            sdkNavigationView.post(nativeGuideMirror);
        }
        if (navigationStarted) {
            if (cycling && bikeHelper != null) {
                bikeHelper.resume();
            } else if (walkHelper != null) {
                walkHelper.resume();
            }
        }
    }

    @Override
    protected void onPause() {
        mirroringNativeGuide = false;
        if (sdkNavigationView != null) sdkNavigationView.removeCallbacks(nativeGuideMirror);
        if (navigationStarted) {
            if (cycling && bikeHelper != null) {
                bikeHelper.pause();
            } else if (walkHelper != null) {
                walkHelper.pause();
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mirroringNativeGuide = false;
        if (sdkNavigationView != null) {
            sdkNavigationView.removeCallbacks(nativeGuideMirror);
            sdkNavigationView.removeCallbacks(rerouteStatusTimeout);
        }
        if (!handOffToGuidePage) {
            if (cycling && bikeHelper != null) {
                bikeHelper.quit();
            } else if (walkHelper != null) {
                walkHelper.quit();
            }
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
