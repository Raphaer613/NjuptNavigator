package com.example.njuptcampusnavigator;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.lang.reflect.Method;
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
import com.baidu.mapapi.utils.CoordinateConverter;
import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.walknavi.adapter.IWEngineInitListener;
import com.baidu.mapapi.walknavi.adapter.IWRoutePlanListener;
import com.baidu.mapapi.walknavi.adapter.IWTTSPlayer;
import com.baidu.mapapi.walknavi.model.WalkRoutePlanError;
import com.baidu.mapapi.walknavi.model.WalkNaviDisplayOption;
import com.baidu.mapapi.walknavi.WalkNavigateHelper;
import com.baidu.mapapi.walknavi.params.WalkRouteNodeInfo;
import com.baidu.mapapi.walknavi.params.WalkNaviLaunchParam;
import com.baidu.mapapi.bikenavi.adapter.IBNaviStatusListener;
import com.baidu.mapapi.walknavi.adapter.IWNaviStatusListener;
import com.baidu.platform.comapi.walknavi.WalkNaviModeSwitchListener;

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
    private AlertDialog exitDialog;
    private TextView remainingInfo;
    private DirectionArrowView guideFallbackIcon;
    private TextView guideLabel;
    private TextView guidePrimary;
    private TextView guideSecondary;
    private boolean hasSdkGuideText;
    private boolean hasVoiceGuideText;
    private int naviLocationCallbackCount;
    private LocationClient guidanceLocationClient;
    private LocationManager systemLocationManager;
    private boolean baiduLocationSeen;
    private boolean systemLocationSeen;
    private LatLng recentGuidancePoint;
    private float recentGuidanceAccuracy;
    private String recentGuidanceSource;
    private long recentGuidanceAt;
    private final LocationListener systemLocationListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            if (location == null) return;
            if (!systemLocationSeen) {
                systemLocationSeen = true;
                Log.i(TAG, "路线匹配收到系统定位，来源=" + location.getProvider()
                        + "，精度=" + (location.hasAccuracy() ? location.getAccuracy() : "未知"));
            }
            if (!location.hasAccuracy() || !Float.isFinite(location.getAccuracy())) return;
            long ageMs = (android.os.SystemClock.elapsedRealtimeNanos()
                    - location.getElapsedRealtimeNanos()) / 1000000L;
            if (ageMs < 0 || ageMs > 10000) return;
            try {
                LatLng gps = new LatLng(location.getLatitude(), location.getLongitude());
                LatLng bd09 = new CoordinateConverter()
                        .from(CoordinateConverter.CoordType.GPS).coord(gps).convert();
                if (bd09 != null) updateGeometryGuidance(bd09, location.getAccuracy(),
                        "系统" + location.getProvider());
            } catch (RuntimeException error) {
                Log.w(TAG, "系统定位坐标转换失败", error);
            }
        }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
        @Override public void onProviderEnabled(String provider) { }
        @Override public void onProviderDisabled(String provider) {
            Log.w(TAG, "路线匹配定位来源已关闭：" + provider);
        }
    };
    private final ArrayList<LatLng> guidancePath = new ArrayList<>();
    private final ArrayList<RouteTurn> guidanceTurns = new ArrayList<>();
    private String guidanceRouteKey = "";
    private double lastMatchedMetres = -1;
    private long lastMatchedAt;
    private int stableTurnFixes;
    private int stableTurnIndex = -1;
    private String lastGeometrySpeech = "";
    private long lastGeometrySpeechAt;
    private long lastGeometryDiagnosticAt;
    private final BDAbstractLocationListener guidanceLocationListener = new BDAbstractLocationListener() {
        @Override public void onReceiveLocation(BDLocation location) {
            if (!baiduLocationSeen && location != null) {
                baiduLocationSeen = true;
                Log.i(TAG, "路线匹配收到百度定位，类型=" + location.getLocType()
                        + "，精度=" + location.getRadius());
            }
            runOnUiThread(() -> updateGeometryGuidance(location));
        }
    };
    private long lastSdkGuideTextAt;
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
            walkHelper.setWalkNaviStatusListener(new IWNaviStatusListener() {
                @Override public void onWalkNaviModeChange(int mode, WalkNaviModeSwitchListener listener) {
                    walkHelper.switchWalkNaviMode(NaviGuideActivity.this, mode, listener);
                }
                @Override public void onNaviExit() {
                    runOnUiThread(() -> showExitConfirmation());
                }
            });
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
            startGuidanceLocation();
            scheduleRouteCapabilityProbe(navigationView);
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
                    .showDialogWithExitNavi(false)
                    .setNaviTextTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
                    .setAutoChangeNaviMode(false);
            walkHelper.setWalkNaviDisplayOption(option);
        }
    }

    // Diagnostic only: identify whether this installed BWNavi build exposes
    // ordered geometry from the very route its navigation engine is following.
    private void scheduleRouteCapabilityProbe(View navigationView) {
        navigationView.postDelayed(this::logRouteCapabilities, 300L);
        navigationView.postDelayed(this::logRouteCapabilities, 2500L);
        navigationView.postDelayed(this::logRouteCapabilities, 12000L);
        navigationView.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed() && !baiduLocationSeen && !systemLocationSeen) {
                Log.w(TAG, "路线匹配无定位回调：百度与系统定位均未返回，请检查设备定位服务");
            }
        }, 9000L);
    }

    private void logRouteCapabilities() {
        if (isFinishing() || isDestroyed()) return;
        try {
            Object result;
            List<RouteInfo> routes;
            List<?> positions;
            if (cycling) {
                BikeRouteResult bikeRoute = bikeHelper == null ? null : bikeHelper.getBikeNaviRouteInfo();
                result = bikeRoute;
                routes = bikeRoute == null ? null : bikeRoute.getRouteInfos();
                positions = bikeRoute == null ? null : bikeRoute.getPositions();
            } else {
                WalkRouteResult walkRoute = walkHelper == null ? null : walkHelper.getWalkNaviRouteInfo();
                result = walkRoute;
                routes = walkRoute == null ? null : walkRoute.getRouteInfos();
                positions = walkRoute == null ? null : walkRoute.getPositions();
            }
            Log.i(TAG, "路线能力：导航结果=" + (result == null ? "null" : result.getClass().getName())
                    + "，路段数=" + (routes == null ? "null" : routes.size())
                    + "，定位回调次数=" + naviLocationCallbackCount);
            Object firstPosition = positions == null || positions.isEmpty() ? null : positions.get(0);
            Object mapLocation = cycling
                    ? bikeHelper == null || bikeHelper.getNaviMap() == null
                        ? null : bikeHelper.getNaviMap().getMap().getLocationData()
                    : walkHelper == null || walkHelper.getNaviMap() == null
                        ? null : walkHelper.getNaviMap().getMap().getLocationData();
            Log.i(TAG, "路线坐标核验：点数=" + (positions == null ? "null" : positions.size())
                    + "，首点类型=" + (firstPosition == null ? "null" : firstPosition.getClass().getName())
                    + "，导航地图定位=" + (mapLocation == null ? "null" : mapLocation.getClass().getName()));
            prepareGeometryRoute(positions);
            logRouteAccessorMethods("导航结果", result);
            if (routes != null && !routes.isEmpty() && routes.get(0) != null) {
                RouteInfo route = routes.get(0);
                logRouteAccessorMethods("路段", route);
                List<StepInfo> steps = route.getStepInfos();
                Log.i(TAG, "路线能力：首路段步骤数=" + (steps == null ? "null" : steps.size()));
                if (steps != null && !steps.isEmpty()) logRouteAccessorMethods("步骤", steps.get(0));
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "读取导航路线能力失败", error);
        }
    }

    // The map's blue route is a separate view of this same SDK route. Match a
    // fresh BD-09 location to its ordered geometry; never infer direction from
    // remaining distance, compass orientation, or enum numeric values.
    private static final class RouteTurn {
        final int vertex;
        final double metres;
        final boolean left;
        final double angle;
        RouteTurn(int vertex, double metres, boolean left, double angle) {
            this.vertex = vertex;
            this.metres = metres;
            this.left = left;
            this.angle = angle;
        }
    }

    private static final class RouteMatch {
        final double metres;
        final double offset;
        RouteMatch(double metres, double offset) {
            this.metres = metres;
            this.offset = offset;
        }
    }

    private void startGuidanceLocation() {
        if (guidanceLocationClient != null || systemLocationManager != null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "路线匹配暂停：缺少精确定位权限");
            return;
        }
        try {
            systemLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (systemLocationManager != null) {
                boolean gps = systemLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                boolean network = systemLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                if (gps) systemLocationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 1000L, 0f,
                        systemLocationListener, Looper.getMainLooper());
                if (network) systemLocationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 2000L, 0f,
                        systemLocationListener, Looper.getMainLooper());
                Log.i(TAG, "路线匹配系统定位已订阅，GPS=" + gps + "，网络=" + network);
            }
        } catch (SecurityException | IllegalArgumentException error) {
            Log.w(TAG, "路线匹配系统定位不可用", error);
            systemLocationManager = null;
        }
        try {
            guidanceLocationClient = new LocationClient(getApplicationContext());
            LocationClientOption option = new LocationClientOption();
            option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
            option.setCoorType("bd09ll");
            option.setScanSpan(1000);
            option.setOpenGps(true);
            option.setLocationNotify(true);
            option.setIsNeedAddress(false);
            guidanceLocationClient.setLocOption(option);
            guidanceLocationClient.registerLocationListener(guidanceLocationListener);
            guidanceLocationClient.start();
            Log.i(TAG, "路线匹配定位已启动（bd09ll）");
        } catch (Exception error) {
            Log.e(TAG, "路线匹配定位启动失败", error);
            if (guidanceLocationClient != null) {
                try { guidanceLocationClient.stop(); } catch (RuntimeException ignored) { }
                guidanceLocationClient = null;
            }
        }
    }

    private void stopGuidanceLocation() {
        if (systemLocationManager != null) {
            try {
                systemLocationManager.removeUpdates(systemLocationListener);
            } catch (SecurityException error) {
                Log.w(TAG, "停止系统定位监听失败", error);
            }
            systemLocationManager = null;
        }
        if (guidanceLocationClient != null) {
            try {
                guidanceLocationClient.unRegisterLocationListener(guidanceLocationListener);
                guidanceLocationClient.stop();
            } catch (RuntimeException error) {
                Log.w(TAG, "停止百度路线匹配定位失败", error);
            }
            guidanceLocationClient = null;
        }
    }

    private void prepareGeometryRoute(List<?> positions) {
        if (positions == null || positions.size() < 3
                || !(positions.get(0) instanceof LatLng)
                || !(positions.get(positions.size() - 1) instanceof LatLng)) return;
        LatLng first = (LatLng) positions.get(0);
        LatLng last = (LatLng) positions.get(positions.size() - 1);
        String key = positions.size() + ":" + first.latitude + ":" + first.longitude
                + ":" + last.latitude + ":" + last.longitude;
        if (key.equals(guidanceRouteKey)) return;
        ArrayList<LatLng> points = new ArrayList<>();
        for (Object item : positions) {
            if (!(item instanceof LatLng)) return;
            LatLng point = (LatLng) item;
            if (points.isEmpty() || metresBetween(points.get(points.size() - 1), point) > 0.6) {
                points.add(point);
            }
        }
        if (points.size() < 3) return;
        double forward = metresBetween(points.get(0), startPoint())
                + metresBetween(points.get(points.size() - 1), endPoint());
        double reverse = metresBetween(points.get(0), endPoint())
                + metresBetween(points.get(points.size() - 1), startPoint());
        if (reverse < forward) Collections.reverse(points);
        double startGap = metresBetween(points.get(0), startPoint());
        double endGap = metresBetween(points.get(points.size() - 1), endPoint());
        if (startGap > 90 || endGap > 90) {
            Log.w(TAG, "路线匹配暂停：路线端点与规划端点不一致，起="
                    + Math.round(startGap) + "米，终=" + Math.round(endGap) + "米");
            return;
        }
        guidancePath.clear();
        guidancePath.addAll(points);
        guidanceTurns.clear();
        double[] cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumulative[i] = cumulative[i - 1] + metresBetween(points.get(i - 1), points.get(i));
        }
        for (int i = 1; i + 1 < points.size(); i++) {
            // Look around each vertex rather than using two very short shape points.
            int before = i - 1, after = i + 1;
            while (before > 0 && cumulative[i] - cumulative[before] < 10) before--;
            while (after < points.size() - 1 && cumulative[after] - cumulative[i] < 10) after++;
            if (cumulative[i] - cumulative[before] < 7 || cumulative[after] - cumulative[i] < 7) continue;
            double scale = Math.cos(Math.toRadians(points.get(i).latitude));
            double ax = (points.get(i).longitude - points.get(before).longitude) * scale;
            double ay = points.get(i).latitude - points.get(before).latitude;
            double bx = (points.get(after).longitude - points.get(i).longitude) * scale;
            double by = points.get(after).latitude - points.get(i).latitude;
            double cross = ax * by - ay * bx;
            double dot = ax * bx + ay * by;
            double angle = Math.toDegrees(Math.atan2(Math.abs(cross), dot));
            if (angle < 58 || angle > 155) continue;
            RouteTurn turn = new RouteTurn(i, cumulative[i], cross > 0, angle);
            if (!guidanceTurns.isEmpty()
                    && turn.metres - guidanceTurns.get(guidanceTurns.size() - 1).metres < 12) {
                int lastIndex = guidanceTurns.size() - 1;
                if (angle > guidanceTurns.get(lastIndex).angle) guidanceTurns.set(lastIndex, turn);
            } else {
                guidanceTurns.add(turn);
            }
        }
        guidanceRouteKey = key;
        lastMatchedMetres = -1;
        stableTurnIndex = -1;
        stableTurnFixes = 0;
        lastGeometrySpeech = "";
        Log.i(TAG, "路线匹配就绪：点数=" + points.size() + "，里程="
                + Math.round(cumulative[cumulative.length - 1]) + "米，明显转向=" + guidanceTurns.size());
        if (recentGuidancePoint != null
                && android.os.SystemClock.elapsedRealtime() - recentGuidanceAt < 10000L) {
            Log.i(TAG, "路线就绪，使用刚收到的实时定位尝试匹配");
            updateGeometryGuidance(recentGuidancePoint, recentGuidanceAccuracy, recentGuidanceSource);
        }
        if (guidanceLocationClient != null) {
            try {
                guidanceLocationClient.requestLocation();
                Log.i(TAG, "路线就绪，主动请求下一次百度定位");
            } catch (RuntimeException error) {
                Log.w(TAG, "主动请求定位失败", error);
            }
        }
    }

    private static double metresBetween(LatLng a, LatLng b) {
        double north = Math.toRadians(b.latitude - a.latitude) * 6371000;
        double east = Math.toRadians(b.longitude - a.longitude) * 6371000
                * Math.cos(Math.toRadians((a.latitude + b.latitude) / 2));
        return Math.hypot(north, east);
    }

    private RouteMatch matchPosition(LatLng position) {
        double bestOffset = Double.MAX_VALUE, bestMetres = 0;
        double otherOffset = Double.MAX_VALUE, otherMetres = 0;
        double cumulative = 0;
        double cos = Math.cos(Math.toRadians(position.latitude));
        for (int i = 1; i < guidancePath.size(); i++) {
            LatLng a = guidancePath.get(i - 1), b = guidancePath.get(i);
            double ax = Math.toRadians(a.longitude - position.longitude) * 6371000 * cos;
            double ay = Math.toRadians(a.latitude - position.latitude) * 6371000;
            double bx = Math.toRadians(b.longitude - a.longitude) * 6371000 * cos;
            double by = Math.toRadians(b.latitude - a.latitude) * 6371000;
            double lengthSquared = bx * bx + by * by;
            if (lengthSquared < 0.1) continue;
            double t = Math.max(0, Math.min(1, -(ax * bx + ay * by) / lengthSquared));
            double offset = Math.hypot(ax + t * bx, ay + t * by);
            double along = cumulative + t * Math.sqrt(lengthSquared);
            if (offset < bestOffset) {
                otherOffset = bestOffset; otherMetres = bestMetres;
                bestOffset = offset; bestMetres = along;
            } else if (offset < otherOffset && Math.abs(along - bestMetres) > 35) {
                otherOffset = offset; otherMetres = along;
            }
            cumulative += metresBetween(a, b);
        }
        // Parallel/looping roads can be indistinguishable at GPS accuracy.
        if (otherOffset - bestOffset < 5 && Math.abs(otherMetres - bestMetres) > 35) return null;
        return bestOffset == Double.MAX_VALUE ? null : new RouteMatch(bestMetres, bestOffset);
    }

    private void updateGeometryGuidance(BDLocation location) {
        if (location == null) return;
        if (location.getLocType() != 61 && location.getLocType() != 161) {
            logGeometryStatus("百度定位类型=" + location.getLocType());
            return;
        }
        float accuracy = location.getRadius();
        if (!Float.isFinite(accuracy) || accuracy <= 0) {
            logGeometryStatus("百度定位精度无效=" + accuracy);
            return;
        }
        LatLng point = new LatLng(location.getLatitude(), location.getLongitude());
        updateGeometryGuidance(point, accuracy, "百度");
    }

    private void updateGeometryGuidance(LatLng point, float accuracy, String source) {
        if (point != null && Float.isFinite(accuracy) && accuracy > 0) {
            recentGuidancePoint = point;
            recentGuidanceAccuracy = accuracy;
            recentGuidanceSource = source;
            recentGuidanceAt = android.os.SystemClock.elapsedRealtime();
        }
        if (!navigationStarted || arrived || rerouting || awaitingGuideAfterYaw
                || isFinishing() || isDestroyed() || point == null || guidancePath.isEmpty()) return;
        // SDK instructions, when available, always outrank geometry estimates.
        if ((hasSdkGuideText && android.os.SystemClock.elapsedRealtime() - lastSdkGuideTextAt < 20000)
                || (hasVoiceGuideText && android.os.SystemClock.elapsedRealtime() - lastRealtimeGuideAt < 20000)) return;
        if (!Float.isFinite(accuracy) || accuracy <= 0 || accuracy > 35) {
            logGeometryStatus(source + "定位精度=" + accuracy + "米");
            return;
        }
        if (point.latitude == 0 || point.longitude == 0) return;
        RouteMatch match = matchPosition(point);
        if (match == null || match.offset > Math.min(40, Math.max(15, accuracy * 1.4))) {
            logGeometryStatus(source + "未匹配路线，距离="
                    + (match == null ? "多路径歧义" : Math.round(match.offset) + "米"));
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastMatchedMetres >= 0 && now - lastMatchedAt < 10000
                && (match.metres < lastMatchedMetres - 18
                    || match.metres > lastMatchedMetres + (cycling ? 80 : 45))) {
            logGeometryStatus(source + "定位跳变，进度=" + Math.round(match.metres) + "米");
            return;
        }
        lastMatchedMetres = match.metres;
        lastMatchedAt = now;
        RouteTurn next = null;
        for (RouteTurn turn : guidanceTurns) {
            if (turn.metres >= match.metres - 4) { next = turn; break; }
        }
        int turnIndex = next == null ? -1 : next.vertex;
        if (turnIndex == stableTurnIndex) stableTurnFixes++;
        else { stableTurnIndex = turnIndex; stableTurnFixes = 1; }
        logGeometryStatus(source + "匹配进度=" + Math.round(match.metres) + "米，偏移="
                + Math.round(match.offset) + "米，下次转弯="
                + (next == null ? "无" : Math.round(next.metres - match.metres) + "米"
                    + (next.left ? "左" : "右")));
        // A precise GPS fix can be used immediately; network fixes need a
        // second independent observation before announcing a direction.
        if (stableTurnFixes < 2
                && !(source.contains("gps") && accuracy <= 12 && match.offset <= 8)) return;
        if (next == null || next.metres - match.metres > (cycling ? 150 : 110)) {
            renderGuide("沿规划路线行进", "以地图蓝色路线为准");
            return;
        }
        int distance = Math.max(0, (int) Math.round(next.metres - match.metres));
        String instruction = "前方" + distance + "米" + (next.left ? "左转" : "右转");
        renderGuide(instruction, "沿地图蓝色路线行进");
        int stage = distance <= (cycling ? 25 : 18) ? 2
                : distance <= (cycling ? 80 : 55) ? 1 : 0;
        String speechKey = guidanceRouteKey + ":" + next.vertex + ":" + stage;
        if (stage > 0 && !speechKey.equals(lastGeometrySpeech)
                && now - lastGeometrySpeechAt >= 12000) {
            lastGeometrySpeech = speechKey;
            lastGeometrySpeechAt = now;
            Log.i(TAG, "路线匹配播报：" + instruction + "，来源=" + source
                    + "，偏移=" + Math.round(match.offset)
                    + "米，精度=" + Math.round(accuracy) + "米");
            speakNavigationText(instruction, true);
        }
    }

    private void logGeometryStatus(String status) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastGeometryDiagnosticAt < 12000) return;
        lastGeometryDiagnosticAt = now;
        Log.i(TAG, "路线匹配状态：" + status);
    }

    private void logRouteAccessorMethods(String label, Object source) {
        if (source == null) return;
        StringBuilder methods = new StringBuilder();
        for (Method method : source.getClass().getMethods()) {
            String name = method.getName();
            if (method.getParameterTypes().length != 0 || !name.startsWith("get")
                    || name.equals("getClass")) continue;
            if (methods.length() > 0) methods.append(", ");
            methods.append(name).append(':').append(method.getReturnType().getSimpleName());
            if (methods.length() > 1400) break;
        }
        Log.i(TAG, "路线能力：" + label + "接口=" + methods);
    }

    private void startBikeGuidePage() {
        try {
            bikeHelper = BikeNavigateHelper.getInstance();
            bikeHelper.setBikeNaviDisplayOption(new BikeNaviDisplayOption()
                    .showTopGuideLayout(true)
                    .showDialogEnable(false)
                    .setNaviTextTypeface(Typeface.create("sans-serif", Typeface.NORMAL)));
            bikeHelper.setBikeNaviStatusListener(new IBNaviStatusListener() {
                @Override public void onNaviExit() {
                    runOnUiThread(() -> showExitConfirmation());
                }
            });
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
            startGuidanceLocation();
            scheduleRouteCapabilityProbe(navigationView);
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
        // Put our own exit control above the SDK's bottom-left exit target.
        // The SDK's default dialog varies by version; this button always uses our dialog.
        TextView appExitButton = new TextView(this);
        appExitButton.setText("×\n退出");
        appExitButton.setTextSize(15);
        appExitButton.setTextColor(Color.rgb(11, 61, 99));
        appExitButton.setGravity(Gravity.CENTER);
        appExitButton.setContentDescription("退出导航");
        GradientDrawable exitSurface = new GradientDrawable();
        exitSurface.setColor(Color.WHITE);
        exitSurface.setCornerRadius(dp(12));
        exitSurface.setStroke(dp(1), Color.rgb(217, 229, 240));
        appExitButton.setBackground(exitSurface);
        appExitButton.setElevation(dp(5));
        appExitButton.setOnClickListener(v -> showExitConfirmation());
        FrameLayout.LayoutParams appExitParams = new FrameLayout.LayoutParams(
                dp(78), dp(70), Gravity.BOTTOM | Gravity.START);
        appExitParams.setMargins(dp(8), 0, 0, dp(8));
        navigationArea.addView(appExitButton, appExitParams);
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
        Log.i(TAG, "自定义导航退出按钮和确认框已装载，模式=" + (cycling ? "骑行" : "步行"));
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
        // Keep the SDK remaining distance for display; never infer a turn from it.
        final String distanceText = distance == null ? null : distance.toString().trim();
        final String timeText = time == null ? null : time.toString().trim();
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || arrived) return;
            if (distanceText != null && !distanceText.isEmpty()) remainingDistance = distanceText;
            if (timeText != null && !timeText.isEmpty()) remainingTime = timeText;
            if (distanceText != null && !distanceText.isEmpty()) {
                lastRemainingUpdateAt = android.os.SystemClock.elapsedRealtime();
            }
            renderRemainingInfo();
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
            showGuideStatus("正在获取新的路线指引…");
            hasSdkGuideText = false;
            hasVoiceGuideText = false;
            lastSdkGuideTextAt = 0L;
            lastNativePrompt = "";
            lastRealtimeGuideAt = 0L;
            lastSpokenManeuver = "";
            lastManeuverSpeechAt = 0L;
            lastManeuverStage = 0;
            renderRemainingInfo();
            Log.i(TAG, "重新规划完成，恢复自定义导航界面");
            if (sdkNavigationView != null) sdkNavigationView.postDelayed(this::logRouteCapabilities, 2500L);
            guidanceRouteKey = "";
            guidancePath.clear();
            guidanceTurns.clear();
            lastMatchedMetres = -1;
            stableTurnIndex = -1;
            stableTurnFixes = 0;
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
                renderRemainingInfo();
                Log.i(TAG, "收到新导航提示，恢复偏航后的自定义界面：" + candidate);
            }
            hasSdkGuideText = true;
            lastSdkGuideTextAt = android.os.SystemClock.elapsedRealtime();
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
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastGuideDiagnosticAt > 10000) {
                lastGuideDiagnosticAt = now;
                Log.i(TAG, "诱导状态：模式=" + (cycling ? "骑行" : "步行")
                        + "，原生提示=0，文字回调=" + hasSdkGuideText
                        + "，语音回调=" + hasVoiceGuideText);
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
            if (isFinishing() || isDestroyed() || arrived || rerouting) return;
            hasVoiceGuideText = true;
            // A previous text callback may stop updating while TTS continues.
            // Prefer fresh on-screen guidance, then mirror the SDK's own speech text.
            if (guidePrimary != null && android.os.SystemClock.elapsedRealtime()
                    - lastSdkGuideTextAt > 2000L) {
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
        @Override public void onRouteGuideIconUpdate(Drawable icon) {
            Log.i(TAG, (cycling ? "骑行" : "步行") + "原生图标回调：" + (icon != null));
        }
        @Override public void onRouteGuideKind(RouteGuideKind kind) {
            Log.i(TAG, (cycling ? "骑行" : "步行") + "原生转向类型回调：" + kind);
        }
        @Override public void onRoadGuideTextUpdate(CharSequence first, CharSequence second) {
            Log.i(TAG, "步行文字回调：" + first + " / " + second);
            updateGuideText(first, second);
        }
        @Override public void onGpsStatusChange(CharSequence text, Drawable icon) { }
        @Override public void onVibrate() { }
        @Override public void onNaviLocationUpdate() {
            if (++naviLocationCallbackCount == 1) Log.i(TAG, "步行导航收到首次位置更新");
        }
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
        @Override public void onRouteGuideIconUpdate(Drawable icon) {
            Log.i(TAG, (cycling ? "骑行" : "步行") + "原生图标回调：" + (icon != null));
        }
        @Override public void onRouteGuideKind(RouteGuideKind kind) {
            Log.i(TAG, (cycling ? "骑行" : "步行") + "原生转向类型回调：" + kind);
        }
        @Override public void onRoadGuideTextUpdate(CharSequence first, CharSequence second) {
            Log.i(TAG, "骑行文字回调：" + first + " / " + second);
            updateGuideText(first, second);
        }
        @Override public void onGpsStatusChange(CharSequence text, Drawable icon) { }
        @Override public void onVibrate() { }
        @Override public void onRouteGuideIconInfoUpdate(IBRouteIconInfo info) { }
        @Override public void onGetRouteDetailInfo(BikeRouteDetailInfo info) { }
        @Override public void onNaviLocationUpdate() {
            if (++naviLocationCallbackCount == 1) Log.i(TAG, "骑行导航收到首次位置更新");
        }
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
        updateVoiceGuide(text);
        // SDK speech is another source of fresh guidance when the text callback is absent.
        if (hasManeuver(text) && !text.contains("偏离") && !text.contains("重新规划")) {
            runOnUiThread(() -> {
                if (!awaitingGuideAfterYaw || arrived || isFinishing() || isDestroyed()) return;
                rerouting = false;
                awaitingGuideAfterYaw = false;
                reroutingStartedAt = 0L;
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

    private void showExitConfirmation() {
        Log.i(TAG, "打开应用自定义退出导航确认框");
        if (isFinishing() || isDestroyed() || !navigationStarted
                || (exitDialog != null && exitDialog.isShowing())) return;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(20));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(20));
        background.setStroke(dp(1), Color.rgb(217, 229, 240));
        card.setBackground(background);

        TextView icon = new TextView(this);
        icon.setText("↩");
        icon.setTextSize(27);
        icon.setTextColor(Color.rgb(0, 104, 183));
        icon.setGravity(Gravity.CENTER);
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setColor(Color.rgb(234, 244, 251));
        iconBackground.setCornerRadius(dp(14));
        icon.setBackground(iconBackground);
        card.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

        TextView title = new TextView(this);
        title.setText("退出导航？");
        title.setTextSize(20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setTextColor(Color.rgb(11, 61, 99));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(16);
        card.addView(title, titleParams);

        TextView description = new TextView(this);
        description.setText("退出后将停止当前路线指引和语音播报。");
        description.setTextSize(14);
        description.setTextColor(Color.rgb(102, 120, 138));
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(6);
        card.addView(description, descriptionParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(48));
        actionsParams.topMargin = dp(24);
        card.addView(actions, actionsParams);

        TextView continueButton = exitAction("继续导航", false);
        TextView exitButton = exitAction("退出导航", true);
        actions.addView(continueButton, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams exitParams = new LinearLayout.LayoutParams(0, -1, 1);
        exitParams.leftMargin = dp(12);
        actions.addView(exitButton, exitParams);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(card).create();
        exitDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (exitDialog == dialog) exitDialog = null;
        });
        continueButton.setOnClickListener(v -> dialog.dismiss());
        exitButton.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.34f;
            window.setAttributes(attributes);
            window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(400)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private TextView exitAction(String label, boolean primary) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? Color.WHITE : Color.rgb(0, 104, 183));
        button.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? Color.rgb(0, 104, 183) : Color.rgb(234, 244, 251));
        background.setCornerRadius(dp(12));
        button.setBackground(background);
        return button;
    }

    @Override
    public void onBackPressed() {
        if (navigationStarted && !isFinishing()) {
            showExitConfirmation();
        } else {
            super.onBackPressed();
        }
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
            if (guidanceLocationClient == null) startGuidanceLocation();
        }
    }

    @Override
    protected void onPause() {
        stopGuidanceLocation();
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
        stopGuidanceLocation();
        if (exitDialog != null) {
            exitDialog.dismiss();
            exitDialog = null;
        }
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
