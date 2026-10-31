package com.example.njuptcampusnavigator;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeOption;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.baidu.mapapi.search.route.BikingRouteLine;
import com.baidu.mapapi.search.route.BikingRoutePlanOption;
import com.baidu.mapapi.search.route.BikingRouteResult;
import com.baidu.mapapi.search.route.DrivingRouteResult;
import com.baidu.mapapi.search.route.IndoorRouteResult;
import com.baidu.mapapi.search.route.IntegralRouteResult;
import com.baidu.mapapi.search.route.MassTransitRouteResult;
import com.baidu.mapapi.search.route.OnGetRoutePlanResultListener;
import com.baidu.mapapi.search.route.PlanNode;
import com.baidu.mapapi.search.route.RoutePlanSearch;
import com.baidu.mapapi.search.route.TransitRouteResult;
import com.baidu.mapapi.search.route.WalkingRouteLine;
import com.baidu.mapapi.search.route.WalkingRoutePlanOption;
import com.baidu.mapapi.search.route.WalkingRouteResult;
import com.baidu.mapapi.search.weather.OnGetWeatherResultListener;
import com.baidu.mapapi.search.weather.WeatherDataType;
import com.baidu.mapapi.search.weather.WeatherResult;
import com.baidu.mapapi.search.weather.WeatherSearch;
import com.baidu.mapapi.search.weather.WeatherSearchForecasts;
import com.baidu.mapapi.search.weather.WeatherSearchLocation;
import com.baidu.mapapi.search.weather.WeatherSearchOption;
import com.baidu.mapapi.search.weather.WeatherSearchRealTime;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity implements SensorEventListener {
    private static final String ACTIVITY_STORAGE = "campus_activities";
    private static final String ACTIVITY_STORAGE_KEY = "saved_activities";
    private static final String POPULARITY_STORAGE = "popular_places";
    private static final int LOCATION_REQUEST = 1001;
    private static final long CURRENT_LOCATION_TIMEOUT_MS = 15000L;
    private static final long RECENT_LOCATION_MAX_AGE_MS = 4000L;
    private static final String DEEP_LINK_SCHEME = "njuptnav";
    private static final String DEEP_LINK_PLACE_HOST = "place";
    private static final String BAIDU_URI_SOURCE = "andr.com.example.njuptcampusnavigator";

    // NJUPT-inspired visual system: one blue family, quiet neutrals, restrained accents.
    private static final String COLOR_PRIMARY = "#0068B7";
    private static final String COLOR_PRIMARY_DARK = "#0B3D63";
    private static final String COLOR_PRIMARY_MID = "#178ACB";
    private static final String COLOR_PRIMARY_SOFT = "#EAF4FB";
    private static final String COLOR_CANVAS = "#F5F8FC";
    private static final String COLOR_SURFACE = "#FFFFFF";
    private static final String COLOR_TEXT = "#17324D";
    private static final String COLOR_TEXT_SECONDARY = "#66788A";
    private static final String COLOR_BORDER = "#D9E5F0";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Place> places = CampusData.defaultPlaces();
    private LatLng currentPoint = CampusData.CAMPUS_CENTER;
    private boolean hasValidLocation;
    private int lastLocationType = -1;
    private long lastValidLocationAt;
    private long lastLocationStartAttemptAt;
    private float currentAccuracy = 30f;
    private float currentDirection;
    private boolean hasSensorDirection;
    private Place selectedPlace;
    private CampusMapView mapView;
    private LinearLayout homePage;
    private LinearLayout[] pages;
    private LinearLayout[] tabItems;
    private NavigationIconView[] tabIcons;
    private TextView[] tabLabels;
    private int activeTab = 2;
    private EditText searchInput;
    private EditText startInput;
    private EditText endInput;
    private LinearLayout searchResults;
    private TextView searchResultHeading;
    private ImageView placeImage;
    private LinearLayout popularList;
    private TextView locationStatus;
    private TextView detailText;
    private Button deleteActivityButton;
    private Button activityEntryButton;
    private TextView routeSummary;
    private TextView weatherStatusText;
    private TextView weatherTemperatureText;
    private TextView weatherDetailText;
    private ImageView weatherCurrentIcon;
    private TextView sharePlaceText;
    private LinearLayout weatherForecastList;
    private RadioGroup routeMode;
    private LocationClient locationClient;
    private RoutePlanSearch routePlanSearch;
    private GeoCoder geoCoder;
    private WeatherSearch weatherSearch;
    private TextToSpeech textToSpeech;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private boolean ttsReady;
    private boolean lastRouteCycling;
    private String lastRouteSummary = "尚未规划路线";
    private LatLng routeStartPoint;
    private LatLng routeEndPoint;
    private String routeEndName;
    private LatLng pendingRouteStartPoint;
    private LatLng pendingRouteEndPoint;
    private boolean routeReadyForNavigation;
    private CoordinateCallback pendingCurrentLocationCallback;
    private Runnable currentLocationStatusTimeoutTask;
    private Runnable locationRecoveryTask;

    private interface CoordinateCallback {
        void onResolved(LatLng point, String displayName);
        void onFailure(String message);
    }

    private interface DialogAction {
        boolean run(AlertDialog dialog);
    }

    private final OnGetGeoCoderResultListener geoCoderListener = new OnGetGeoCoderResultListener() {
        @Override
        public void onGetGeoCodeResult(GeoCodeResult result) {
            if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR
                    || result.getLocation() == null) {
                if (geocodeCallback != null) {
                    CoordinateCallback callback = geocodeCallback;
                    geocodeCallback = null;
                    callback.onFailure("百度地图没有找到该地址");
                }
                return;
            }
            if (geocodeCallback != null) {
                CoordinateCallback callback = geocodeCallback;
                geocodeCallback = null;
                String displayName = result.getAddress();
                callback.onResolved(result.getLocation(),
                        displayName == null || displayName.isEmpty() ? pendingGeocodeText : displayName);
            }
        }

        @Override
        public void onGetReverseGeoCodeResult(ReverseGeoCodeResult result) {
        }
    };

    private CoordinateCallback geocodeCallback;
    private String pendingGeocodeText;

    private final BDAbstractLocationListener locationListener = new BDAbstractLocationListener() {
        @Override
        public void onReceiveLocation(BDLocation location) {
            if (location == null) return;

            int type = location.getLocType();
            lastLocationType = type;
            android.util.Log.i("NjuptLocation",
                    "type=" + type
                            + ", lat=" + location.getLatitude()
                            + ", lng=" + location.getLongitude()
                            + ", time=" + location.getTime());

            // 只用本次成功取得的卫星或网络定位更新地图
            if (type != 61 && type != 161) {
                locationStatus.setText("正在获取新的位置，定位状态：" + type);
                return;
            }

            double lat = location.getLatitude();
            double lng = location.getLongitude();
            if (lat == 0 || lng == 0) return;

            LatLng newPoint = new LatLng(lat, lng);
            if (routeReadyForNavigation && routeStartPoint != null && startInput != null) {
                String selectedStart = startInput.getText().toString().trim();
                if ((selectedStart.isEmpty() || selectedStart.equals("当前位置"))
                        && distanceMeters(routeStartPoint, newPoint) > 30f) {
                    invalidatePlannedRoute();
                    routeStartPoint = null;
                    routeSummary.setText("当前位置已变化，请重新规划路线");
                }
            }
            currentPoint = newPoint;
            hasValidLocation = true;
            lastValidLocationAt = android.os.SystemClock.elapsedRealtime();
            cancelLocationRecovery();
            currentAccuracy = location.getRadius();
            if (!hasSensorDirection && location.getDirection() > 0) {
                currentDirection = location.getDirection();
            }

            locationStatus.setText(CampusData.isInsideCampus(currentPoint)
                    ? "当前位置已更新"
                    : "当前位置已更新 · 当前在校园范围外");

            updateCurrentPointOnMap();
            mapView.centerOnCurrentPoint(currentPoint);
            if (currentLocationStatusTimeoutTask != null) {
                mainHandler.removeCallbacks(currentLocationStatusTimeoutTask);
                currentLocationStatusTimeoutTask = null;
            }
            notifyFreshCurrentLocation();
        }
    };

    private final OnGetRoutePlanResultListener routeListener = new OnGetRoutePlanResultListener() {
        @Override
        public void onGetWalkingRouteResult(WalkingRouteResult result) {
            if (pendingRouteStartPoint == null || pendingRouteEndPoint == null) {
                return;
            }
            if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR
                    || result.getRouteLines() == null || result.getRouteLines().isEmpty()) {
                showRouteFailure("百度地图未找到可用步行路线，错误码："
                        + routeError(result) + "，可能是校内道路未被百度步行路网覆盖");
                return;
            }
            WalkingRouteLine line = result.getRouteLines().get(0);
            List<LatLng> points = new ArrayList<>();
            for (WalkingRouteLine.WalkingStep step : line.getAllStep()) {
                if (step.getWayPoints() != null) {
                    points.addAll(step.getWayPoints());
                }
            }
            showRoute(points, false, line.getDistance(), line.getDuration());
        }

        @Override
        public void onGetBikingRouteResult(BikingRouteResult result) {
            if (pendingRouteStartPoint == null || pendingRouteEndPoint == null) {
                return;
            }
            if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR
                    || result.getRouteLines() == null || result.getRouteLines().isEmpty()) {
                showRouteFailure("百度地图未找到可用骑行路线，错误码："
                        + routeError(result) + "，可能是校内道路未被百度骑行路网覆盖");
                return;
            }
            BikingRouteLine line = result.getRouteLines().get(0);
            List<LatLng> points = new ArrayList<>();
            for (BikingRouteLine.BikingStep step : line.getAllStep()) {
                if (step.getWayPoints() != null) {
                    points.addAll(step.getWayPoints());
                }
            }
            showRoute(points, true, line.getDistance(), line.getDuration());
        }

        @Override
        public void onGetTransitRouteResult(TransitRouteResult result) {
        }

        @Override
        public void onGetMassTransitRouteResult(MassTransitRouteResult result) {
        }

        @Override
        public void onGetDrivingRouteResult(DrivingRouteResult result) {
        }

        @Override
        public void onGetIndoorRouteResult(IndoorRouteResult result) {
        }

        @Override
        public void onGetIntegralRouteResult(IntegralRouteResult result) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(color(COLOR_PRIMARY_DARK));
        routePlanSearch = RoutePlanSearch.newInstance();
        routePlanSearch.setOnGetRoutePlanResultListener(routeListener);
        geoCoder = GeoCoder.newInstance();
        geoCoder.setOnGetGeoCodeResultListener(geoCoderListener);
        loadSavedActivities();
        buildScreen();
        setupWeather();
        setupTextToSpeech();
        setupDirectionSensor();
        setupLocation();
        handleDeepLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDeepLink(intent);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(COLOR_CANVAS));
        // Keep controls outside status/navigation bars, including Android 15 edge-to-edge.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        homePage = vertical(0, 0, 0, 0);

        LinearLayout header = horizontal(16, 14, 16, 12);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("南邮校园导航", 23, COLOR_PRIMARY_DARK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        titleStack.addView(title);
        locationStatus = text("百度地图初始化中 · 使用校园中心点", 13, COLOR_TEXT_SECONDARY);
        titleStack.addView(locationStatus);
        header.addView(titleStack, new LinearLayout.LayoutParams(0, -2, 1));
        activityEntryButton = actionButton("活动", COLOR_PRIMARY);
        activityEntryButton.setOnClickListener(v -> showActivityManager());
        header.addView(activityEntryButton, wrap());
        updateActivityEntryButton();
        homePage.addView(header);

        mapView = new CampusMapView(this);
        mapView.setPlaces(places);
        updateCurrentPointOnMap();
        mapView.setOnPlaceClickListener(this::selectPlace);
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        mapParams.setMargins(dp(16), 0, dp(16), dp(8));
        homePage.addView(mapView, mapParams);

        final int routeControlHeight = dp(48);
        final int routeGap = dp(8);
        final int routeSectionGap = dp(12);

        LinearLayout routePanel = vertical(16, 16, 16, 16);
        routePanel.setBackground(roundBackground(COLOR_SURFACE, 14));
        routePanel.setElevation(dp(2));
        TextView routeTitle = text("路线规划", 16, COLOR_PRIMARY_DARK);
        routeTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        routePanel.addView(routeTitle);

        LinearLayout startRow = horizontal(0, 0, 0, 0);
        startRow.setGravity(Gravity.CENTER_VERTICAL);
        startInput = routeInput("起点：南校门");
        startRow.addView(startInput, new LinearLayout.LayoutParams(0, routeControlHeight, 1));
        Button currentButton = actionButton("当前位置", COLOR_PRIMARY_SOFT);
        currentButton.setTextColor(color(COLOR_PRIMARY));
        currentButton.setOnClickListener(v -> {
            startInput.setText("当前位置");
            routeStartPoint = null;
            invalidatePlannedRoute();
            routeSummary.setText("起点已设为当前位置，规划路线时会重新获取实时定位");
        });
        LinearLayout.LayoutParams currentButtonParams =
                new LinearLayout.LayoutParams(dp(92), routeControlHeight);
        currentButtonParams.setMargins(dp(8), 0, 0, 0);
        startRow.addView(currentButton, currentButtonParams);
        LinearLayout.LayoutParams startRowParams = new LinearLayout.LayoutParams(-1, -2);
        startRowParams.topMargin = routeSectionGap;
        routePanel.addView(startRow, startRowParams);

        endInput = routeInput("终点：图书馆");
        LinearLayout endRow = horizontal(0, 0, 0, 0);
        endRow.setGravity(Gravity.CENTER_VERTICAL);
        endRow.addView(endInput, new LinearLayout.LayoutParams(0, routeControlHeight, 1));
        Button swapButton = actionButton("⇅", COLOR_PRIMARY_SOFT);
        swapButton.setTextColor(color(COLOR_PRIMARY));
        swapButton.setTextSize(24);
        swapButton.setPadding(0, 0, 0, 0);
        swapButton.setContentDescription("交换出发地和目的地");
        swapButton.setOnClickListener(v -> swapRouteEndpoints());
        LinearLayout.LayoutParams swapParams =
                new LinearLayout.LayoutParams(routeControlHeight, routeControlHeight);
        swapParams.setMargins(dp(8), 0, 0, 0);
        endRow.addView(swapButton, swapParams);
        LinearLayout.LayoutParams endRowParams = new LinearLayout.LayoutParams(-1, -2);
        endRowParams.topMargin = routeGap;
        routePanel.addView(endRow, endRowParams);

        LinearLayout routeLine = horizontal(0, 0, 0, 0);
        routeLine.setGravity(Gravity.CENTER_VERTICAL);
        routeMode = new RadioGroup(this);
        routeMode.setOrientation(RadioGroup.HORIZONTAL);
        routeMode.setGravity(Gravity.CENTER_VERTICAL);
        RadioButton walk = new RadioButton(this);
        walk.setId(View.generateViewId());
        walk.setText("步行");
        walk.setTextSize(14);
        walk.setChecked(true);
        RadioButton cycle = new RadioButton(this);
        cycle.setId(View.generateViewId());
        cycle.setText("骑行");
        cycle.setTextSize(14);
        routeMode.addView(walk);
        routeMode.addView(cycle);
        routeLine.addView(routeMode, new LinearLayout.LayoutParams(0, routeControlHeight, 1));
        Button planButton = actionButton("规划路线", COLOR_PRIMARY);
        planButton.setOnClickListener(v -> planRoute());
        routeLine.addView(planButton, new LinearLayout.LayoutParams(-2, routeControlHeight));
        LinearLayout.LayoutParams routeLineParams = new LinearLayout.LayoutParams(-1, -2);
        routeLineParams.topMargin = routeGap;
        routePanel.addView(routeLine, routeLineParams);

        routeSummary = text("输入起点和终点后规划路线，也可以直接选择校园地点", 13, COLOR_TEXT_SECONDARY);
        routeSummary.setPadding(0, 0, 0, 0);
        routeSummary.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
        summaryParams.topMargin = routeSectionGap;
        routePanel.addView(routeSummary, summaryParams);
        Button navigationButton = actionButton("开始导航", COLOR_PRIMARY_DARK);
        navigationButton.setOnClickListener(v -> startNavigation());
        LinearLayout.LayoutParams navigationParams =
                new LinearLayout.LayoutParams(-1, routeControlHeight);
        navigationParams.topMargin = routeSectionGap;
        routePanel.addView(navigationButton, navigationParams);
        ScrollView bottomScroll = new ScrollView(this);
        bottomScroll.setFillViewport(true);
        LinearLayout bottom = vertical(16, 0, 16, 12);
        bottom.addView(routePanel, new LinearLayout.LayoutParams(-1, -2));
        TextView detailTitle = text("地点详情", 16, COLOR_PRIMARY_DARK);
        detailTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        detailTitle.setPadding(0, dp(10), 0, dp(6));
        bottom.addView(detailTitle);

        // 地点详情卡片
        LinearLayout detailCard = horizontal(12, 14, 12, 14);
        detailCard.setGravity(Gravity.TOP);
        detailCard.setMinimumHeight(dp(100));
        detailCard.setBackground(roundBackground(COLOR_SURFACE, 14));
        detailCard.setElevation(dp(2));

// 左侧图片占位：104 × 104，保持 1:1
        placeImage = new ImageView(this);
        placeImage.setContentDescription("地点图片");
        placeImage.setBackground(roundBackground(COLOR_PRIMARY_SOFT, 12));
        placeImage.setImageResource(R.drawable.ic_place_default);
        placeImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(dp(104), dp(104));
        imageParams.setMargins(0, 0, dp(12), 0);
        detailCard.addView(placeImage, imageParams);

// 右侧详情文字，继续由原有选点逻辑更新
        detailText = text(
                "点击地图标记或热门地点查看开放时间、楼层和主要功能。",
                14,
                COLOR_TEXT_SECONDARY
        );
        detailCard.addView(
                detailText,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        bottom.addView(
                detailCard,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        deleteActivityButton = actionButton("删除此活动", COLOR_PRIMARY_SOFT);
        deleteActivityButton.setTextColor(color(COLOR_PRIMARY_DARK));
        deleteActivityButton.setVisibility(View.GONE);
        deleteActivityButton.setOnClickListener(v -> confirmDeleteSelectedActivity());
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(-1, dp(44));
        deleteParams.topMargin = dp(10);
        bottom.addView(deleteActivityButton, deleteParams);

        bottomScroll.addView(bottom);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1.5f);
        homePage.addView(bottomScroll, scrollParams);

        pages = new LinearLayout[]{buildOtherPage(), buildSearchPage(), homePage,
                buildPopularPage(), buildSharePage()};
        FrameLayout pageHost = new FrameLayout(this);
        for (LinearLayout page : pages) {
            pageHost.addView(page, new FrameLayout.LayoutParams(-1, -1));
        }
        root.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(buildBottomNavigation(), new LinearLayout.LayoutParams(-1, dp(64)));
        setContentView(root);
        root.requestApplyInsets();
        selectTab(2);
    }

    private LinearLayout page(String title, String subtitle) {
        LinearLayout page = vertical(16, 18, 16, 12);
        TextView heading = text(title, 23, COLOR_PRIMARY_DARK);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        page.addView(heading);
        TextView description = text(subtitle, 14, COLOR_TEXT_SECONDARY);
        description.setPadding(0, dp(6), 0, dp(18));
        page.addView(description);
        return page;
    }

    private void placeholder(LinearLayout parent, String title, String description) {
        LinearLayout card = vertical(16, 16, 16, 16);
        card.setBackground(roundBackground(COLOR_SURFACE, 14));
        card.setElevation(dp(2));
        TextView heading = text(title, 17, COLOR_PRIMARY_DARK);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(heading);
        TextView body = text(description, 14, COLOR_TEXT_SECONDARY);
        body.setPadding(0, dp(8), 0, 0);
        card.addView(body);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        parent.addView(card, params);
    }

    private void webLink(LinearLayout parent, String title, String description, String url) {
        LinearLayout card = vertical(16, 16, 16, 16);
        card.setBackground(roundBackground(COLOR_SURFACE, 14));
        card.setElevation(dp(2));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(title + "，点击打开");

        TextView heading = text(title, 17, COLOR_PRIMARY_DARK);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(heading);

        TextView body = text(description, 14, COLOR_PRIMARY);
        body.setPadding(0, dp(8), 0, 0);
        card.addView(body);

        card.setOnClickListener(view -> openWebPage(url));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        parent.addView(card, params);
    }

    private void openWebPage(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "未找到可打开网页的应用", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout buildOtherPage() {
        LinearLayout page = page("其它", "校园服务与常用入口");
        ScrollView scroll = new ScrollView(this);
        LinearLayout cards = vertical(0, 0, 0, 0);
        cards.addView(buildWeatherCard());
        webLink(cards,
                "南京邮电大学官网",
                "点击访问南邮校园官网  ›",
                "https://www.njupt.edu.cn/");
        scroll.addView(cards);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return page;
    }

    private LinearLayout buildWeatherCard() {
        LinearLayout card = vertical(16, 16, 16, 16);
        card.setBackground(roundBackground(COLOR_SURFACE, 14));
        card.setElevation(dp(2));

        LinearLayout headingRow = horizontal(0, 0, 0, 0);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout headingText = vertical(0, 0, 0, 0);
        TextView heading = text("校园天气 · 栖霞区", 17, COLOR_PRIMARY_DARK);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        headingText.addView(heading);

        weatherStatusText = text("正在获取天气……", 14, COLOR_TEXT_SECONDARY);
        weatherStatusText.setPadding(0, dp(5), 0, 0);
        headingText.addView(weatherStatusText);
        headingRow.addView(headingText, new LinearLayout.LayoutParams(0, -2, 1));

        Button refresh = actionButton("刷新", COLOR_PRIMARY);
        refresh.setOnClickListener(view -> loadCampusWeather());
        headingRow.addView(refresh, new LinearLayout.LayoutParams(dp(72), dp(40)));
        card.addView(headingRow);

        LinearLayout currentWeatherRow = horizontal(0, 14, 0, 0);
        currentWeatherRow.setGravity(Gravity.CENTER_VERTICAL);
        weatherTemperatureText = text("--℃", 36, COLOR_PRIMARY_DARK);
        weatherTemperatureText.setTypeface(null, android.graphics.Typeface.BOLD);
        currentWeatherRow.addView(weatherTemperatureText, new LinearLayout.LayoutParams(0, -2, 1));
        weatherCurrentIcon = new ImageView(this);
        weatherCurrentIcon.setImageResource(R.drawable.ic_weather_unknown);
        weatherCurrentIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        weatherCurrentIcon.setContentDescription("天气未知");
        currentWeatherRow.addView(weatherCurrentIcon, new LinearLayout.LayoutParams(dp(56), dp(56)));
        card.addView(currentWeatherRow);

        weatherDetailText = text("天气数据加载后将在这里显示", 14, COLOR_TEXT_SECONDARY);
        weatherDetailText.setPadding(0, dp(6), 0, dp(14));
        card.addView(weatherDetailText);

        TextView forecastHeading = text("未来三天", 15, COLOR_PRIMARY_DARK);
        forecastHeading.setTypeface(null, android.graphics.Typeface.BOLD);
        forecastHeading.setPadding(0, dp(4), 0, dp(8));
        card.addView(forecastHeading);

        weatherForecastList = vertical(0, 0, 0, 0);
        weatherForecastList.addView(text("预报加载中……", 13, COLOR_TEXT_SECONDARY));
        card.addView(weatherForecastList);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private void setupWeather() {
        weatherSearch = WeatherSearch.newInstance();
        weatherSearch.setWeatherSearchResultListener(new OnGetWeatherResultListener() {
            @Override
            public void onGetWeatherResultListener(WeatherResult weatherResult) {
                showWeatherResult(weatherResult);
            }
        });
        loadCampusWeather();
    }

    private void loadCampusWeather() {
        if (weatherSearch == null || weatherStatusText == null) {
            return;
        }

        weatherStatusText.setText("正在获取天气……");
        weatherTemperatureText.setText("--℃");
        weatherCurrentIcon.setImageResource(R.drawable.ic_weather_unknown);
        weatherCurrentIcon.setContentDescription("天气正在加载");
        weatherDetailText.setText("正在连接百度天气服务");
        weatherForecastList.removeAllViews();
        weatherForecastList.addView(text("预报加载中……", 13, COLOR_TEXT_SECONDARY));

        WeatherSearchOption option = new WeatherSearchOption()
                .weatherDataType(WeatherDataType.WEATHER_DATA_TYPE_ALL)
                .districtID("320113");

        if (!weatherSearch.request(option)) {
            showWeatherError("天气请求未能发出，请稍后重试");
        }
    }

    private void showWeatherResult(WeatherResult result) {
        if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
            String reason = result == null ? "没有收到天气数据" : result.error.toString();
            showWeatherError("天气加载失败：" + reason);
            return;
        }

        WeatherSearchRealTime realTime = result.getRealTimeWeather();
        if (realTime == null) {
            showWeatherError("暂时没有栖霞区天气数据");
            return;
        }

        WeatherSearchLocation location = result.getLocation();
        String area = location == null ? "栖霞区" : valueOrDefault(location.getDistrictName(), "栖霞区");
        String phenomenon = valueOrDefault(realTime.getPhenomenon(), "天气未知");
        weatherStatusText.setText(area + " · " + phenomenon);
        weatherTemperatureText.setText(formatTemperature(realTime.getTemperature()));
        weatherCurrentIcon.setImageResource(weatherIconFor(phenomenon));
        weatherCurrentIcon.setContentDescription("当前天气：" + phenomenon);

        StringBuilder details = new StringBuilder();
        if (realTime.getSensoryTemp() != 999999) {
            details.append("体感 ").append(realTime.getSensoryTemp()).append("℃");
        }
        if (realTime.getRelativeHumidity() != 999999) {
            appendWeatherDetail(details, "湿度 " + realTime.getRelativeHumidity() + "%");
        }
        String wind = joinWeatherText(realTime.getWindDirection(), realTime.getWindPower());
        if (!wind.isEmpty()) {
            appendWeatherDetail(details, wind);
        }
        if (realTime.getUpdateTime() != null && !realTime.getUpdateTime().trim().isEmpty()) {
            appendWeatherDetail(details, "更新于 " + realTime.getUpdateTime());
        }
        weatherDetailText.setText(details.length() == 0 ? "实时天气" : details.toString());

        weatherForecastList.removeAllViews();
        List<WeatherSearchForecasts> forecasts = result.getForecasts();
        if (forecasts == null || forecasts.isEmpty()) {
            weatherForecastList.addView(text("暂无未来天气预报", 13, COLOR_TEXT_SECONDARY));
            return;
        }

        int count = Math.min(3, forecasts.size());
        for (int i = 0; i < count; i++) {
            weatherForecastList.addView(buildForecastRow(forecasts.get(i), i));
        }
    }

    private View buildForecastRow(WeatherSearchForecasts forecast, int index) {
        LinearLayout row = horizontal(10, 9, 10, 9);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(roundBackground(index % 2 == 0 ? COLOR_PRIMARY_SOFT : COLOR_SURFACE, 10));

        String day;
        if (index == 0) {
            day = "今天";
        } else if (index == 1) {
            day = "明天";
        } else {
            day = valueOrDefault(forecast.getWeek(), "后天");
        }

        TextView dayView = text(day, 14, COLOR_PRIMARY_DARK);
        row.addView(dayView, new LinearLayout.LayoutParams(0, -2, 0.8f));

        String weather = valueOrDefault(forecast.getPhenomenonDay(),
                valueOrDefault(forecast.getPhenomenonNight(), "暂无"));
        LinearLayout weatherCell = horizontal(0, 0, 0, 0);
        weatherCell.setGravity(Gravity.CENTER_VERTICAL);
        ImageView forecastIcon = new ImageView(this);
        forecastIcon.setImageResource(weatherIconFor(weather));
        forecastIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        forecastIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams forecastIconParams =
                new LinearLayout.LayoutParams(dp(22), dp(22));
        forecastIconParams.setMargins(0, 0, dp(5), 0);
        weatherCell.addView(forecastIcon, forecastIconParams);
        TextView weatherView = text(weather, 14, COLOR_TEXT_SECONDARY);
        weatherView.setSingleLine(true);
        weatherView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        weatherCell.addView(weatherView, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(weatherCell, new LinearLayout.LayoutParams(0, -2, 1.4f));

        String temperature = formatForecastTemperature(
                forecast.getLowestTemp(), forecast.getHighestTemp());
        TextView temperatureView = text(temperature, 14, COLOR_PRIMARY_DARK);
        temperatureView.setGravity(Gravity.END);
        row.addView(temperatureView, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(6));
        row.setLayoutParams(params);
        return row;
    }

    private void showWeatherError(String message) {
        if (weatherStatusText == null) {
            return;
        }
        weatherStatusText.setText("天气暂不可用");
        weatherTemperatureText.setText("--℃");
        weatherCurrentIcon.setImageResource(R.drawable.ic_weather_unknown);
        weatherCurrentIcon.setContentDescription("天气暂不可用");
        weatherDetailText.setText(message);
        weatherForecastList.removeAllViews();
        weatherForecastList.addView(text("点击右上角“刷新”重试", 13, COLOR_TEXT_SECONDARY));
    }

    private String formatTemperature(int temperature) {
        return temperature == 999999 ? "--℃" : temperature + "℃";
    }

    private int weatherIconFor(String phenomenon) {
        if (phenomenon == null || phenomenon.trim().isEmpty()) {
            return R.drawable.ic_weather_unknown;
        }
        if (phenomenon.contains("雷")) return R.drawable.ic_weather_thunder;
        if (phenomenon.contains("雪")) return R.drawable.ic_weather_snow;
        if (phenomenon.contains("雨")) return R.drawable.ic_weather_rain;
        if (phenomenon.contains("雾") || phenomenon.contains("霾")
                || phenomenon.contains("沙") || phenomenon.contains("尘")) {
            return R.drawable.ic_weather_haze;
        }
        if (phenomenon.contains("多云")
                || (phenomenon.contains("晴")
                && (phenomenon.contains("云") || phenomenon.contains("阴")))) {
            return R.drawable.ic_weather_partly_cloudy;
        }
        if (phenomenon.contains("阴") || phenomenon.contains("云")) {
            return R.drawable.ic_weather_cloudy;
        }
        if (phenomenon.contains("晴")) return R.drawable.ic_weather_sunny;
        return R.drawable.ic_weather_unknown;
    }

    private String formatForecastTemperature(int low, int high) {
        String lowText = low == 999999 ? "--" : String.valueOf(low);
        String highText = high == 999999 ? "--" : String.valueOf(high);
        return lowText + "～" + highText + "℃";
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String joinWeatherText(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return left + " " + right;
    }

    private void appendWeatherDetail(StringBuilder builder, String detail) {
        if (detail == null || detail.trim().isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("  ·  ");
        }
        builder.append(detail);
    }

    private LinearLayout buildSearchPage() {
        LinearLayout page = page("搜索", "查找校园地点");
        LinearLayout row = horizontal(0, 0, 0, 10);
        searchInput = routeInput("搜索名称、类别或功能");
        searchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchInput.setOnEditorActionListener((view, action, event) -> {
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                searchPlaces();
                return true;
            }
            return false;
        });
        row.addView(searchInput, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button search = actionButton("搜索", COLOR_PRIMARY);
        search.setOnClickListener(v -> searchPlaces());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(76), dp(48));
        buttonParams.setMargins(dp(8), 0, 0, 0);
        row.addView(search, buttonParams);
        page.addView(row);
        searchResultHeading = text("搜索结果", 16, COLOR_PRIMARY_DARK);
        searchResultHeading.setPadding(0, dp(18), 0, dp(8));
        page.addView(searchResultHeading);
        ScrollView resultsScroll = new ScrollView(this);
        searchResults = vertical(0, 0, 0, 0);
        searchResults.addView(text("输入关键词后搜索校园地点。", 14, COLOR_TEXT_SECONDARY));
        resultsScroll.addView(searchResults);
        page.addView(resultsScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return page;
    }

    private LinearLayout buildPopularPage() {
        LinearLayout page = page("热门地点推荐", "校园常用地点 · 点击查看地图与详情");
        ScrollView scroll = new ScrollView(this);
        popularList = vertical(0, 0, 0, 0);
        refreshPopularPlaces();
        scroll.addView(popularList);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return page;
    }

    private boolean isPopularCandidate(Place place) {
        return !place.temporary && (place.category.equals("教学楼")
                || place.name.equals("图书馆")
                || place.name.contains("食堂")
                || place.name.contains("操场"));
    }

    private int localPlaceViews(Place place) {
        return getSharedPreferences(POPULARITY_STORAGE, MODE_PRIVATE)
                .getInt(place.name, 0);
    }

    private void recordPlaceView(Place place) {
        if (!isPopularCandidate(place)) return;
        int views = localPlaceViews(place);
        getSharedPreferences(POPULARITY_STORAGE, MODE_PRIVATE).edit()
                .putInt(place.name, Math.min(views + 1, 1000000000)).apply();
        refreshPopularPlaces();
    }

    private void refreshPopularPlaces() {
        if (popularList == null) return;
        List<Place> ranked = new ArrayList<>();
        for (Place place : places) {
            if (isPopularCandidate(place)) ranked.add(place);
        }
        // No views yet: stable order from CampusData. More views move a place up.
        ranked.sort((left, right) -> {
            int byViews = Integer.compare(localPlaceViews(right), localPlaceViews(left));
            return byViews != 0 ? byViews
                    : Integer.compare(places.indexOf(left), places.indexOf(right));
        });
        popularList.removeAllViews();
        for (int i = 0; i < ranked.size(); i++) {
            popularList.addView(popularPlaceCard(ranked.get(i), i));
        }
    }

    private View popularPlaceCard(Place place, int index) {
        LinearLayout card = horizontal(14, 12, 14, 12);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(roundBackground(COLOR_SURFACE, 14));
        card.setElevation(dp(2));
        card.setClickable(true);
        card.setOnClickListener(v -> selectPlace(place));

        LinearLayout badge = vertical(0, 0, 0, 0);
        badge.setGravity(Gravity.CENTER);
        if (index < 3) {
            String[] golds = {"#C99427", "#E0B451", "#EBCD83"};
            ImageView flame = new ImageView(this);
            flame.setImageResource(R.drawable.ic_hot_flame);
            flame.setColorFilter(color(golds[index]));
            flame.setContentDescription("热门第" + (index + 1) + "名");
            badge.addView(flame, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }
        TextView rank = text(String.format(Locale.ROOT, "%02d", index + 1),
                12, index < 3 ? COLOR_PRIMARY_DARK : COLOR_TEXT_SECONDARY);
        rank.setGravity(Gravity.CENTER);
        badge.addView(rank);
        card.addView(badge, new LinearLayout.LayoutParams(dp(34), -2));

        LinearLayout labels = vertical(0, 0, 0, 0);
        TextView name = text(place.name, 16, COLOR_PRIMARY_DARK);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        labels.addView(name);
        TextView category = text(place.category + " · 点击查看地图与详情",
                12, COLOR_TEXT_SECONDARY);
        category.setPadding(0, dp(4), 0, 0);
        labels.addView(category);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelsParams.setMargins(dp(12), 0, 0, 0);
        card.addView(labels, labelsParams);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, dp(4), 0, dp(10));
        card.setLayoutParams(cardParams);
        return card;
    }

    private LinearLayout buildSharePage() {
        LinearLayout page = page("分享地点", "地点分享入口");

        LinearLayout card = vertical(16, 16, 16, 16);
        card.setBackground(roundBackground(COLOR_SURFACE, 14));
        card.setElevation(dp(2));

        TextView title = text("当前地点", 17, COLOR_PRIMARY_DARK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);

        sharePlaceText = text(
                "尚未选择地点，请先在首页地图、搜索结果或热门地点中选择一个地点。",
                14,
                COLOR_TEXT_SECONDARY
        );
        sharePlaceText.setPadding(0, dp(8), 0, dp(16));
        card.addView(sharePlaceText);

        Button wechatButton = actionButton("分享到微信", COLOR_PRIMARY);
        wechatButton.setOnClickListener(v -> shareToApp("com.tencent.mm", "微信"));
        card.addView(wechatButton, new LinearLayout.LayoutParams(-1, dp(48)));

        Button qqButton = actionButton("分享到 QQ", COLOR_PRIMARY_MID);
        qqButton.setOnClickListener(v -> shareToApp("com.tencent.mobileqq", "QQ"));
        LinearLayout.LayoutParams qqParams = new LinearLayout.LayoutParams(-1, dp(48));
        qqParams.setMargins(0, dp(10), 0, 0);
        card.addView(qqButton, qqParams);

        Button copyButton = actionButton("复制地点信息", COLOR_PRIMARY_SOFT);
        copyButton.setTextColor(color(COLOR_PRIMARY_DARK));
        copyButton.setOnClickListener(v -> copySelectedPlace());
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-1, dp(48));
        copyParams.setMargins(0, dp(10), 0, 0);
        card.addView(copyButton, copyParams);

        page.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return page;
    }

    private void updateSharePlaceText() {
        if (sharePlaceText == null) {
            return;
        }
        if (selectedPlace == null) {
            sharePlaceText.setText("尚未选择地点，请先选择一个校园地点。");
            return;
        }
        sharePlaceText.setText(selectedPlace.name + "\n" + selectedPlace.category
                + " · " + selectedPlace.openTime + "\n" + selectedPlace.description);
    }

    private String buildPlaceShareText() {
        if (selectedPlace == null) {
            return "";
        }
        return (selectedPlace.temporary && "临时活动".equals(selectedPlace.category)
                ? "【南邮校园活动】" : "【南邮校园地点】")
                + "\n地点：" + selectedPlace.name
                + "\n类别：" + selectedPlace.category
                + (selectedPlace.temporary && "临时活动".equals(selectedPlace.category)
                ? "\n活动时间：" + selectedPlace.openTime + "\n活动地点：" + selectedPlace.floors
                : "\n开放时间：" + selectedPlace.openTime + "\n楼层信息：" + selectedPlace.floors)
                + "\n地点介绍：" + selectedPlace.description
                + "\n百度地图坐标：" + selectedPlace.coordinate.latitude
                + ", " + selectedPlace.coordinate.longitude
                + "\n百度地图网页链接：" + buildBaiduWebMarkerLink(selectedPlace)
                + "\n\n来自“南邮校园导航”";
    }

    private void shareToApp(String packageName, String appName) {
        if (selectedPlace == null) {
            Toast.makeText(this, "请先选择一个要分享的校园地点", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "南邮校园地点：" + selectedPlace.name);
        shareIntent.putExtra(Intent.EXTRA_TEXT, buildPlaceShareText());
        shareIntent.setPackage(packageName);

        try {
            startActivity(shareIntent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "未安装" + appName + "，可以复制地点信息后手动发送",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void copySelectedPlace() {
        if (selectedPlace == null) {
            Toast.makeText(this, "请先选择一个要复制的校园地点", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "暂时无法使用剪贴板", Toast.LENGTH_SHORT).show();
            return;
        }

        clipboard.setPrimaryClip(ClipData.newPlainText("南邮校园地点", buildPlaceShareText()));
        Toast.makeText(this, "地点信息已复制", Toast.LENGTH_SHORT).show();
    }

    private LinearLayout buildBottomNavigation() {
        LinearLayout bar = horizontal(0, 0, 0, 0);
        bar.setBackgroundColor(color(COLOR_SURFACE));
        bar.setElevation(dp(8));
        String[] labels = {"其它", "搜索", "首页", "热门地点", "分享地点"};
        tabItems = new LinearLayout[5];
        tabIcons = new NavigationIconView[5];
        tabLabels = new TextView[5];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout item = vertical(0, 6, 0, 4);
            item.setGravity(Gravity.CENTER);
            item.setContentDescription(labels[i]);
            item.setFocusable(true);
            item.setClickable(true);
            tabIcons[i] = new NavigationIconView(this, i);
            item.addView(tabIcons[i], new LinearLayout.LayoutParams(dp(26), dp(26)));
            tabLabels[i] = text(labels[i], 11, COLOR_TEXT_SECONDARY);
            tabLabels[i].setGravity(Gravity.CENTER);
            tabLabels[i].setSingleLine(true);
            item.addView(tabLabels[i], new LinearLayout.LayoutParams(-1, -2));
            item.setOnClickListener(v -> selectTab(index));
            tabItems[i] = item;
            bar.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
        }
        return bar;
    }

    private void selectTab(int index) {
        if (pages == null) return;
        if (index == 3) refreshPopularPlaces();
        android.view.inputmethod.InputMethodManager keyboard =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null && getCurrentFocus() != null) {
            keyboard.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            getCurrentFocus().clearFocus();
        }
        activeTab = index;
        for (int i = 0; i < pages.length; i++) {
            boolean selected = i == index;
            pages[i].setVisibility(selected ? View.VISIBLE : View.GONE);
            tabItems[i].setSelected(selected);
            tabItems[i].setBackgroundColor(color(selected ? COLOR_PRIMARY_SOFT : COLOR_SURFACE));
            tabIcons[i].setActive(selected);
            tabLabels[i].setTextColor(color(selected ? COLOR_PRIMARY : COLOR_TEXT_SECONDARY));
        }
    }

    @Override
    public void onBackPressed() {
        if (activeTab != 2) {
            selectTab(2);
        } else {
            super.onBackPressed();
        }
    }

    private Button placeButton(Place place) {
        Button button = actionButton(place.name + "   " + place.category, COLOR_SURFACE);
        button.setTextColor(color(COLOR_TEXT));
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setOnClickListener(v -> selectPlace(place));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(44));
        params.setMargins(0, dp(3), 0, dp(3));
        button.setLayoutParams(params);
        return button;
    }

    private void searchPlaces() {
        String query = searchInput.getText().toString().trim();
        searchResults.removeAllViews();
        if (query.isEmpty()) {
            searchResultHeading.setText("搜索结果");
            searchResults.setVisibility(View.VISIBLE);
            searchResults.addView(text("请输入地点名称，例如“图书馆”“食堂”或“校医院”。",
                    14, COLOR_TEXT_SECONDARY));
            Toast.makeText(this, "请输入地点名称", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] keywords = query.toLowerCase(Locale.ROOT).split("\\s+");
        List<Place> matches = new ArrayList<>();
        for (Place place : places) {
            String searchable = (place.name + " " + place.category + " " + place.description)
                    .toLowerCase(Locale.ROOT);
            boolean allMatch = true;
            for (String keyword : keywords) {
                if (!searchable.contains(keyword)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) matches.add(place);
        }
        matches.sort((left, right) -> Integer.compare(
                searchRank(left, query), searchRank(right, query)));
        searchResultHeading.setText("搜索结果 · " + matches.size() + " 处");
        searchResults.setVisibility(View.VISIBLE);
        if (matches.isEmpty()) {
            TextView empty = text("没有找到相关地点。可以尝试建筑名称、类别或用途，例如“图书馆”“食堂”“校医院”。",
                    14, COLOR_TEXT_SECONDARY);
            empty.setPadding(dp(16), dp(2), dp(16), dp(10));
            searchResults.addView(empty);
            return;
        }
        for (Place place : matches) {
            searchResults.addView(searchResultCard(place));
        }
    }

    private int searchRank(Place place, String query) {
        String name = place.name.toLowerCase(Locale.ROOT);
        String term = query.toLowerCase(Locale.ROOT);
        if (name.equals(term)) return 0;
        if (name.startsWith(term)) return 1;
        if (name.contains(term)) return 2;
        if (place.category.toLowerCase(Locale.ROOT).contains(term)) return 3;
        return 4;
    }

    private View searchResultCard(Place place) {
        LinearLayout card = vertical(14, 12, 14, 12);
        card.setBackground(roundBackground(COLOR_SURFACE, 14));
        card.setElevation(dp(2));
        card.setClickable(true);
        card.setOnClickListener(v -> selectPlace(place));
        TextView name = text(place.name + "  ·  " + place.category, 16, COLOR_PRIMARY_DARK);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(name);
        TextView info = text((place.temporary && "临时活动".equals(place.category)
                ? "活动时间 " : "开放时间 ") + place.openTime + "  ·  " + place.floors
                + "\n" + place.description, 13, COLOR_TEXT_SECONDARY);
        info.setPadding(0, dp(5), 0, dp(5));
        card.addView(info);
        TextView action = text("查看地图位置与详细信息  ›", 13, COLOR_PRIMARY);
        card.addView(action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(4), 0, dp(8));
        card.setLayoutParams(params);
        return card;
    }

    private void selectPlace(Place place) {
        recordPlaceView(place);
        selectTab(2);
        selectedPlace = place;
        placeImage.setImageResource(imageForPlace(place));
        updateSharePlaceText();
        routeEndPoint = place.coordinate;
        routeEndName = place.name;
        invalidatePlannedRoute();
        if (endInput != null) {
            endInput.setText(place.name);
        }
        mapView.setSelectedPlace(place);
        detailText.setText(
                place.name + "  ·  " + place.category
                        + (place.temporary && "临时活动".equals(place.category)
                        ? "\n活动时间  " + place.openTime + "\n活动地点  " + place.floors
                        : "\n开放时间  " + place.openTime + "\n楼层信息  " + place.floors)
                        + "\n主要功能  " + place.description
                        + "\n百度坐标  " + place.coordinate.latitude + ", " + place.coordinate.longitude
        );
        routeSummary.setText("终点已设为：" + place.name + "    请选择出行方式后规划");
        deleteActivityButton.setVisibility(place.temporary && "临时活动".equals(place.category)
                ? View.VISIBLE : View.GONE);
    }

    private int imageForPlace(Place place) {
        if (place == null) return R.drawable.ic_place_default;
        switch (place.name) {
            case "南一食堂": return R.drawable.place_canteen_1;
            case "南二食堂": return R.drawable.place_canteen_2;
            case "南三食堂": return R.drawable.place_canteen_3;
            case "战邮广场": return R.drawable.place_zhanyou_square;
            case "图书馆": return R.drawable.place_library;
            default:
                if (place.name.startsWith("教学楼一")) return R.drawable.place_teaching_1;
                if (place.name.startsWith("教学楼二")) return R.drawable.place_teaching_2;
                if (place.name.startsWith("教学楼三")) return R.drawable.place_teaching_3;
                if (place.name.startsWith("教学楼四")) return R.drawable.place_teaching_4;
                return R.drawable.ic_place_default;
        }
    }

    private void confirmDeleteSelectedActivity() {
        confirmDeleteActivity(selectedPlace);
    }

    private void confirmDeleteActivity(Place activity) {
        if (activity == null || !activity.temporary
                || !"临时活动".equals(activity.category)) return;
        TextView message = text("删除“" + activity.name + "”后，地图标记和本机记录将一起移除。",
                14, COLOR_TEXT_SECONDARY);
        showStyledDialog("删除临时活动", "确认移除这个活动标记", "×",
                message, "确认删除", dialog -> {
                    int index = places.indexOf(activity);
                    if (index < 0) return true;
                    places.remove(index);
                    if (!saveActivities()) {
                        places.add(index, activity);
                        Toast.makeText(this, "保存失败，活动没有删除", Toast.LENGTH_LONG).show();
                        return false;
                    }
                    mapView.setPlaces(places);
                    updateActivityEntryButton();
                    if (selectedPlace == activity) {
                        selectedPlace = null;
                        placeImage.setImageResource(R.drawable.ic_place_default);
                        updateSharePlaceText();
                        detailText.setText("点击地图标记或搜索结果查看地点详情。");
                        deleteActivityButton.setVisibility(View.GONE);
                        endInput.setText("");
                        invalidatePlannedRoute();
                        routeEndPoint = null;
                        routeEndName = null;
                        routeSummary.setText("活动已删除，请重新选择目的地");
                    }
                    return true;
                });
    }

    private void swapRouteEndpoints() {
        String previousStart = startInput.getText().toString().trim();
        String previousEnd = endInput.getText().toString().trim();
        if (previousEnd.isEmpty()) {
            Toast.makeText(this, "请先输入目的地", Toast.LENGTH_SHORT).show();
            return;
        }

        LatLng previousStartPoint = previousStart.isEmpty() || previousStart.equals("当前位置")
                ? currentPoint : routeStartPoint;
        LatLng previousEndPoint = routeEndPoint;
        startInput.setText(previousEnd);
        endInput.setText(previousStart.isEmpty() ? "当前位置" : previousStart);
        routeStartPoint = previousEndPoint;
        routeEndPoint = previousStartPoint;
        routeEndName = previousStart.isEmpty() ? "当前位置" : previousStart;
        invalidatePlannedRoute();
        routeSummary.setText("已交换起点和终点，请重新规划路线");
    }

    private void invalidatePlannedRoute() {
        routeReadyForNavigation = false;
        pendingRouteStartPoint = null;
        pendingRouteEndPoint = null;
        geocodeCallback = null;
        pendingCurrentLocationCallback = null;
        cancelLocationRecovery();
        if (mapView != null) {
            mapView.setRoute(null, false);
        }
    }

    private void shareSelectedPlace() {
        if (selectedPlace == null) {
            Toast.makeText(this, "请先选择一个地点", Toast.LENGTH_SHORT).show();
            return;
        }
        String webLink = buildBaiduWebMarkerLink(selectedPlace).toString();
        String message = "我在南邮校园导航中分享了「" + selectedPlace.name + "」：\n"
                + "用网页查看：\n" + webLink;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "南邮校园地点百度地图链接");
        shareIntent.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(shareIntent, "分享地点"));
    }

    private Uri buildBaiduAppMarkerLink(Place place) {
        return new Uri.Builder()
                .scheme("baidumap")
                .authority("map")
                .appendPath("marker")
                .appendQueryParameter("location", place.coordinate.latitude + "," + place.coordinate.longitude)
                .appendQueryParameter("title", place.name)
                .appendQueryParameter("content", place.description)
                .appendQueryParameter("coord_type", "bd09ll")
                .appendQueryParameter("src", BAIDU_URI_SOURCE)
                .build();
    }

    private Uri buildBaiduWebMarkerLink(Place place) {
        return new Uri.Builder()
                .scheme("https")
                .authority("api.map.baidu.com")
                .appendPath("marker")
                .appendQueryParameter("location", place.coordinate.latitude + "," + place.coordinate.longitude)
                .appendQueryParameter("title", place.name)
                .appendQueryParameter("content", place.description)
                .appendQueryParameter("coord_type", "bd09ll")
                .appendQueryParameter("output", "html")
                .appendQueryParameter("src", BAIDU_URI_SOURCE)
                .build();
    }

    private Uri buildPlaceDeepLink(Place place) {
        return new Uri.Builder()
                .scheme(DEEP_LINK_SCHEME)
                .authority(DEEP_LINK_PLACE_HOST)
                .appendQueryParameter("name", place.name)
                .appendQueryParameter("category", place.category)
                .appendQueryParameter("lat", String.valueOf(place.coordinate.latitude))
                .appendQueryParameter("lng", String.valueOf(place.coordinate.longitude))
                .build();
    }

    private void handleDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) {
            return;
        }
        Uri data = intent.getData();
        if (!DEEP_LINK_SCHEME.equals(data.getScheme())
                || !DEEP_LINK_PLACE_HOST.equals(data.getHost())) {
            return;
        }

        String name = data.getQueryParameter("name");
        Place place = findPlaceByName(name);
        if (place == null) {
            place = createSharedPlace(data);
        }
        if (place == null) {
            Toast.makeText(this, "分享链接中的地点信息不完整", Toast.LENGTH_LONG).show();
            return;
        }

        selectPlace(place);
        routeSummary.setText("已打开分享地点：" + place.name + "    可直接规划路线");
    }

    private Place findPlaceByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String value = name.trim();
        for (Place place : places) {
            if (place.name.equals(value)) {
                return place;
            }
        }
        for (Place place : places) {
            if (place.name.contains(value) || value.contains(place.name)) {
                return place;
            }
        }
        return null;
    }

    private Place createSharedPlace(Uri data) {
        try {
            String latText = data.getQueryParameter("lat");
            String lngText = data.getQueryParameter("lng");
            if (latText == null || lngText == null) {
                return null;
            }
            double latitude = Double.parseDouble(latText);
            double longitude = Double.parseDouble(lngText);
            String name = data.getQueryParameter("name");
            if (name == null || name.trim().isEmpty()) {
                name = "分享地点";
            }
            String category = data.getQueryParameter("category");
            if (category == null || category.trim().isEmpty()) {
                category = "分享地点";
            }
            Place sharedPlace = new Place(
                    name,
                    category,
                    "以分享链接为准",
                    "未知",
                    "来自好友分享的校内地点，可作为路线终点使用。",
                    latitude,
                    longitude,
                    true
            );
            places.add(sharedPlace);
            if (mapView != null) {
                mapView.setPlaces(places);
            }
            return sharedPlace;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private void planRoute() {
        String startText = startInput == null ? "" : startInput.getText().toString().trim();
        String endText = endInput == null ? "" : endInput.getText().toString().trim();
        if (endText.isEmpty()) {
            Toast.makeText(this, "请输入终点", Toast.LENGTH_SHORT).show();
            return;
        }
        invalidatePlannedRoute();
        routeSummary.setText("正在解析起点和终点...");
        resolveAddress(startText, true, new CoordinateCallback() {
            @Override
            public void onResolved(LatLng point, String displayName) {
                routeStartPoint = point;
                resolveAddress(endText, false, new CoordinateCallback() {
                    @Override
                    public void onResolved(LatLng endPoint, String resolvedName) {
                        routeEndPoint = endPoint;
                        routeEndName = resolvedName;
                        requestRoute(routeStartPoint, routeEndPoint);
                    }

                    @Override
                    public void onFailure(String message) {
                        showRouteFailure("终点解析失败：" + message);
                    }
                });
            }

            @Override
            public void onFailure(String message) {
                showRouteFailure("起点解析失败：" + message);
            }
        });
    }

    private void resolveAddress(String text, boolean start, CoordinateCallback callback) {
        String value = text == null ? "" : text.trim();
        if ((start && value.isEmpty()) || value.equals("当前位置")) {
            if (start) {
                requestFreshCurrentLocation(callback);
            } else {
                callback.onResolved(currentPoint, "当前位置");
            }
            return;
        }

        for (Place place : places) {
            if (place.name.equals(value) || place.name.contains(value) || value.contains(place.name)) {
                callback.onResolved(place.coordinate, place.name);
                return;
            }
        }

        if (geoCoder == null) {
            callback.onFailure("地理编码服务未初始化");
            return;
        }
        pendingGeocodeText = value;
        geocodeCallback = callback;
        String address = value;
        if (!value.contains("南京") && !value.contains("江苏")) {
            address = "南京市栖霞区南京邮电大学仙林校区" + value;
        }
        boolean accepted = geoCoder.geocode(new GeoCodeOption()
                .city("南京市")
                .address(address));
        if (!accepted) {
            geocodeCallback = null;
            callback.onFailure("地理编码请求未发出");
        }
    }

    private void requestRoute(LatLng startPoint, LatLng endPoint) {
        if (startPoint == null || endPoint == null) {
            showRouteFailure("起点或终点坐标无效");
            return;
        }
        boolean cycling = isCyclingSelected();
        pendingRouteStartPoint = startPoint;
        pendingRouteEndPoint = endPoint;
        routeReadyForNavigation = false;
        PlanNode start = PlanNode.withLocation(startPoint);
        PlanNode end = PlanNode.withLocation(endPoint);
        routeSummary.setText("正在调用百度地图规划" + (cycling ? "骑行" : "步行") + "路线...");
        if (cycling) {
            routePlanSearch.bikingSearch(new BikingRoutePlanOption()
                    .from(start)
                    .to(end)
                    .ridingType(0));
        } else {
            routePlanSearch.walkingSearch(new WalkingRoutePlanOption()
                    .from(start)
                    .to(end));
        }
    }

    private void showRoute(List<LatLng> points, boolean cycling, int distanceMeters, int durationSeconds) {
        lastRouteCycling = cycling;
        routeReadyForNavigation = true;
        mapView.setRoute(points, cycling);
        int minutes = Math.max(1, Math.round(durationSeconds / 60f));
        lastRouteSummary = (cycling ? "骑行" : "步行")
                + "路线已生成  ·  约 " + distanceMeters + " 米  ·  预计 " + minutes + " 分钟";
        routeSummary.setText(lastRouteSummary);
    }

    private void showRouteFailure(String message) {
        lastRouteSummary = message;
        routeReadyForNavigation = false;
        if (pendingRouteStartPoint != null && pendingRouteEndPoint != null) {
            List<LatLng> guidePoints = new ArrayList<>();
            guidePoints.add(pendingRouteStartPoint);
            guidePoints.add(pendingRouteEndPoint);
            mapView.setRoute(guidePoints, isCyclingSelected());
            int directDistance = Math.round(distanceMeters(pendingRouteStartPoint, pendingRouteEndPoint));
            routeSummary.setText(message + "。已显示起终点参考线，直线距离约 "
                    + directDistance + " 米；这不是百度可导航路线。");
        } else {
            routeSummary.setText(message + "。请确认 AK、网络和地点坐标是否可用。");
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String routeError(SearchResult result) {
        if (result == null) {
            return "result_null";
        }
        return result.error == null ? "error_null" : result.error.name();
    }

    private void startNavigation() {
        if (routeEndPoint == null) {
            Toast.makeText(this, "请先输入起点和终点并规划路线", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!routeReadyForNavigation) {
            Toast.makeText(this, "百度路线规划成功后才能开始实时导航", Toast.LENGTH_LONG).show();
            routeSummary.setText("请先点击“规划路线”，并等待百度返回可用步行/骑行路线后再开始导航。");
            return;
        }
        Intent intent = new Intent(this, NaviGuideActivity.class);
        LatLng startPoint = routeStartPoint == null ? currentPoint : routeStartPoint;
        intent.putExtra(NaviGuideActivity.EXTRA_START_LATITUDE, startPoint.latitude);
        intent.putExtra(NaviGuideActivity.EXTRA_START_LONGITUDE, startPoint.longitude);
        intent.putExtra(NaviGuideActivity.EXTRA_END_LATITUDE, routeEndPoint.latitude);
        intent.putExtra(NaviGuideActivity.EXTRA_END_LONGITUDE, routeEndPoint.longitude);
        intent.putExtra(NaviGuideActivity.EXTRA_CYCLING, isCyclingSelected());
        intent.putExtra(NaviGuideActivity.EXTRA_PLACE_NAME,
                routeEndName == null ? endInput.getText().toString().trim() : routeEndName);
        startActivity(intent);
    }

    private void requestFreshCurrentLocation(CoordinateCallback callback) {
        if (currentLocationStatusTimeoutTask != null) {
            mainHandler.removeCallbacks(currentLocationStatusTimeoutTask);
            currentLocationStatusTimeoutTask = null;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (callback != null && hasValidLocation
                && now - lastValidLocationAt <= RECENT_LOCATION_MAX_AGE_MS) {
            android.util.Log.i("NjuptLocation", "使用" + (now - lastValidLocationAt)
                    + "ms内的实时定位规划路线");
            callback.onResolved(currentPoint, "当前位置");
            return;
        }
        if (callback != null) {
            pendingCurrentLocationCallback = callback;
            mainHandler.postDelayed(() -> {
                if (pendingCurrentLocationCallback == callback) {
                    pendingCurrentLocationCallback = null;
                    String reason = locationFailureSummary();
                    locationStatus.setText(reason);
                    callback.onFailure(reason + "；未使用旧坐标规划路线");
                }
            }, CURRENT_LOCATION_TIMEOUT_MS);
        } else {
            currentLocationStatusTimeoutTask = new Runnable() {
                @Override public void run() {
                    if (currentLocationStatusTimeoutTask != this) return;
                    currentLocationStatusTimeoutTask = null;
                    locationStatus.setText(locationFailureSummary());
                }
            };
            mainHandler.postDelayed(currentLocationStatusTimeoutTask, CURRENT_LOCATION_TIMEOUT_MS);
        }
        if (locationClient == null
                && (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            startBaiduLocation();
        }
        if (locationClient == null) {
            if (callback != null) {
                pendingCurrentLocationCallback = null;
                callback.onFailure("百度定位服务未初始化，无法使用当前位置规划路线");
            } else if (currentLocationStatusTimeoutTask != null) {
                mainHandler.removeCallbacks(currentLocationStatusTimeoutTask);
                currentLocationStatusTimeoutTask = null;
                locationStatus.setText("百度定位服务未初始化");
            }
            return;
        }
        locationStatus.setText(callback == null ? "正在刷新当前位置..." : "正在刷新当前位置，最多等待15秒...");
        // Periodic scan normally supplies the next fix. If the client really
        // stopped after its first result, start it again and verify recovery.
        ensureLocationServiceRunning();
        if (callback != null) {
            scheduleLocationRecovery(callback);
        }
        android.util.Log.i("NjuptLocation", "等待连续定位回调，route=" + (callback != null)
                + ", clientStarted=" + locationClient.isStarted()
                + ", lastFixAgeMs=" + (hasValidLocation ? now - lastValidLocationAt : -1));
    }

    private void ensureLocationServiceRunning() {
        if (locationClient == null || locationClient.isStarted()) return;
        long now = android.os.SystemClock.elapsedRealtime();
        // start() is asynchronous: do not restart while a recent start is in flight.
        if (now - lastLocationStartAttemptAt < 3000L) return;
        try {
            lastLocationStartAttemptAt = now;
            locationClient.start();
            android.util.Log.w("NjuptLocation", "定位客户端已停止，再次调用 start()");
        } catch (RuntimeException error) {
            android.util.Log.e("NjuptLocation", "重新启动定位服务失败", error);
        }
    }

    private void scheduleLocationRecovery(CoordinateCallback callback) {
        cancelLocationRecovery();
        locationRecoveryTask = () -> {
            locationRecoveryTask = null;
            if (pendingCurrentLocationCallback != callback || locationClient == null
                    || locationClient.isStarted()) return;
            android.util.Log.w("NjuptLocation", "5秒后服务仍未启动，重新创建定位客户端");
            try {
                locationClient.unRegisterLocationListener(locationListener);
                locationClient.stop();
                locationClient = null;
                startBaiduLocation();
            } catch (RuntimeException error) {
                android.util.Log.e("NjuptLocation", "重建定位客户端失败", error);
            }
        };
        mainHandler.postDelayed(locationRecoveryTask, 5000L);
    }

    private void cancelLocationRecovery() {
        if (locationRecoveryTask != null) {
            mainHandler.removeCallbacks(locationRecoveryTask);
            locationRecoveryTask = null;
        }
    }

    private String locationFailureSummary() {
        long age = hasValidLocation
                ? android.os.SystemClock.elapsedRealtime() - lastValidLocationAt : -1L;
        boolean started = locationClient != null && locationClient.isStarted();
        android.util.Log.w("NjuptLocation", "连续定位超时，clientStarted=" + started
                + ", lastType=" + lastLocationType + ", lastFixAgeMs=" + age);
        return "实时定位超时（服务运行=" + started + "，上次定位类型 "
                + lastLocationType + "），请查看 NjuptLocation 日志";
    }

    private void notifyFreshCurrentLocation() {
        if (pendingCurrentLocationCallback == null) {
            return;
        }
        CoordinateCallback callback = pendingCurrentLocationCallback;
        pendingCurrentLocationCallback = null;
        cancelLocationRecovery();
        callback.onResolved(currentPoint, "当前位置");
    }

    private float distanceMeters(LatLng start, LatLng end) {
        double earthRadius = 6371000d;
        double startLat = Math.toRadians(start.latitude);
        double endLat = Math.toRadians(end.latitude);
        double deltaLat = Math.toRadians(end.latitude - start.latitude);
        double deltaLng = Math.toRadians(end.longitude - start.longitude);
        double a = Math.sin(deltaLat / 2d) * Math.sin(deltaLat / 2d)
                + Math.cos(startLat) * Math.cos(endLat)
                * Math.sin(deltaLng / 2d) * Math.sin(deltaLng / 2d);
        double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
        return (float) (earthRadius * c);
    }

    private int savedActivityCount() {
        int count = 0;
        for (Place place : places) {
            if (place.temporary && "临时活动".equals(place.category)) count++;
        }
        return count;
    }

    private void updateActivityEntryButton() {
        if (activityEntryButton != null) {
            activityEntryButton.setText("活动 " + savedActivityCount());
        }
    }

    private void showActivityManager() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = vertical(0, 0, 0, 0);
        int count = 0;
        final AlertDialog[] manager = new AlertDialog[1];
        for (Place place : places) {
            if (!place.temporary || !"临时活动".equals(place.category)) continue;
            count++;
            LinearLayout card = vertical(12, 12, 12, 12);
            card.setBackground(roundBackground(COLOR_PRIMARY_SOFT, 12));
            TextView title = text(place.name, 16, COLOR_PRIMARY_DARK);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(title);
            TextView details = text("时间：" + place.openTime + "\n地点：" + place.floors,
                    13, COLOR_TEXT_SECONDARY);
            details.setPadding(0, dp(4), 0, dp(8));
            card.addView(details);

            LinearLayout actions = horizontal(0, 0, 0, 0);
            Button locate = actionButton("地图", COLOR_SURFACE);
            locate.setTextColor(color(COLOR_PRIMARY));
            locate.setOnClickListener(v -> {
                manager[0].dismiss();
                selectPlace(place);
            });
            actions.addView(locate, new LinearLayout.LayoutParams(0, dp(42), 1));
            Button share = actionButton("分享", COLOR_SURFACE);
            share.setTextColor(color(COLOR_PRIMARY));
            share.setOnClickListener(v -> {
                manager[0].dismiss();
                selectPlace(place);
                selectTab(4);
            });
            LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, dp(42), 1);
            shareParams.setMargins(dp(6), 0, 0, 0);
            actions.addView(share, shareParams);
            Button delete = actionButton("删除", COLOR_SURFACE);
            delete.setTextColor(color(COLOR_PRIMARY_DARK));
            delete.setOnClickListener(v -> {
                manager[0].dismiss();
                confirmDeleteActivity(place);
            });
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(42), 1);
            deleteParams.setMargins(dp(6), 0, 0, 0);
            actions.addView(delete, deleteParams);
            card.addView(actions);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.bottomMargin = dp(10);
            list.addView(card, cardParams);
        }
        if (count == 0) {
            list.addView(text("还没有添加活动。点击下方按钮创建第一个活动。",
                    14, COLOR_TEXT_SECONDARY));
        }
        scroll.addView(list);
        scroll.setFillViewport(false);
        scroll.setMinimumHeight(dp(90));
        View content = scroll;
        if (count > 3) {
            FrameLayout viewport = new FrameLayout(this);
            viewport.addView(scroll, new FrameLayout.LayoutParams(-1, dp(320)));
            content = viewport;
        }
        manager[0] = showStyledDialog("校园活动", "已保存 " + count + " 个活动",
                "▦", content, "新增活动", dialog -> {
                    dialog.dismiss();
                    showAddActivityDialog();
                    return false;
                });
    }

    private void showAddActivityDialog() {
        LinearLayout form = vertical(0, 2, 0, 0);
        EditText nameInput = dialogInput("例如：校园讲座");
        EditText locationInput = dialogInput("例如：大学生活动中心一楼 / 当前位置");
        EditText timeInput = dialogInput("例如：周六 14:00—16:00");
        EditText notesInput = dialogInput("例如：报告厅二楼，欢迎参加");
        form.addView(dialogField("活动名称", nameInput));

        LinearLayout.LayoutParams locationParams = new LinearLayout.LayoutParams(-1, -2);
        locationParams.setMargins(0, dp(14), 0, 0);
        form.addView(dialogField("活动地点（必填）", locationInput), locationParams);

        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-1, -2);
        timeParams.topMargin = dp(12);
        form.addView(dialogField("活动时间", timeInput), timeParams);
        LinearLayout.LayoutParams notesParams = new LinearLayout.LayoutParams(-1, -2);
        notesParams.topMargin = dp(12);
        form.addView(dialogField("补充说明", notesInput), notesParams);

        final boolean[] resolving = {false};

        showStyledDialog(
                "添加临时活动",
                "输入校园地点名称，标记会放在对应位置",
                "▦",
                form,
                "添加活动",
                dialog -> {
                    if (resolving[0]) return false;
                    String name = nameInput.getText().toString().trim();
                    String location = locationInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        nameInput.setError("请输入活动名称");
                        return false;
                    }
                    if (location.isEmpty()) {
                        locationInput.setError("请输入校园地点或当前位置");
                        return false;
                    }
                    String time = timeInput.getText().toString().trim();
                    String notes = notesInput.getText().toString().trim();
                    resolving[0] = true;
                    resolveActivityAddress(location, new CoordinateCallback() {
                        @Override
                        public void onResolved(LatLng point, String resolvedName) {
                            if (!dialog.isShowing()) return;
                            resolving[0] = false;
                            if (!CampusData.isInsideCampus(point)) {
                                locationInput.setError("位置不在仙林校区，请填写更具体的校园地点");
                                return;
                            }
                            Place activity = new Place(
                                    name,
                                    "临时活动",
                                    time.isEmpty() ? "以活动通知为准" : time,
                                    location,
                                    notes.isEmpty() ? "活动地点：" + location
                                            : "活动地点：" + location + "。" + notes,
                                    point.latitude,
                                    point.longitude,
                                    true
                            );
                            places.add(activity);
                            if (!saveActivities()) {
                                places.remove(activity);
                                Toast.makeText(MainActivity.this,
                                        "活动保存失败，请重试", Toast.LENGTH_LONG).show();
                                return;
                            }
                            mapView.setPlaces(places);
                            selectPlace(activity);
                            updateActivityEntryButton();
                            dialog.dismiss();
                            Toast.makeText(MainActivity.this,
                                    "活动已标记，可在“分享地点”中发送给好友", Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onFailure(String message) {
                            if (!dialog.isShowing()) return;
                            resolving[0] = false;
                            locationInput.setError(message);
                            Toast.makeText(MainActivity.this,
                                    "未能定位活动地点，请输入具体的校园建筑名称", Toast.LENGTH_LONG).show();
                        }
                    });
                    return false;
                }
        );
    }

    private void resolveActivityAddress(String venue, CoordinateCallback callback) {
        if ("当前位置".equals(venue)) {
            requestFreshCurrentLocation(callback);
            return;
        }
        Place bestMatch = null;
        int matchCount = 0;
        for (Place place : places) {
            if (place.temporary) continue;
            if (venue.equals(place.name)) {
                callback.onResolved(place.coordinate, place.name);
                return;
            }
            if (venue.contains(place.name) || place.name.contains(venue)) {
                bestMatch = place;
                matchCount++;
            }
        }
        if (matchCount > 1) {
            callback.onFailure("匹配到多个校园地点，请输入完整建筑名称");
        } else if (bestMatch != null) {
            callback.onResolved(bestMatch.coordinate, bestMatch.name);
        } else {
            // For a campus venue not in the built-in list, try Baidu geocoding.
            resolveAddress(venue, false, callback);
        }
    }

    private void loadSavedActivities() {
        SharedPreferences prefs = getSharedPreferences(ACTIVITY_STORAGE, MODE_PRIVATE);
        String saved = prefs.getString(ACTIVITY_STORAGE_KEY, "[]");
        try {
            JSONArray items = new JSONArray(saved);
            int loaded = 0;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                double latitude = item.optDouble("latitude", Double.NaN);
                double longitude = item.optDouble("longitude", Double.NaN);
                if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                        || latitude < -90 || latitude > 90
                        || longitude < -180 || longitude > 180) {
                    android.util.Log.w("NjuptActivity", "跳过无效坐标的活动记录 " + i);
                    continue;
                }
                String name = item.optString("name").trim();
                if (name.isEmpty()) continue;
                places.add(new Place(name, "临时活动",
                        item.optString("time", "以活动通知为准"),
                        item.optString("venue", "校园活动地点"),
                        item.optString("description", "校园临时活动"),
                        latitude, longitude, true));
                loaded++;
            }
            android.util.Log.i("NjuptActivity", "读取活动记录=" + items.length()
                    + "，成功加载=" + loaded);
        } catch (JSONException error) {
            android.util.Log.e("NjuptActivity", "读取已保存的活动失败", error);
        }
    }

    private boolean saveActivities() {
        JSONArray items = new JSONArray();
        try {
            for (Place place : places) {
                if (!place.temporary || !"临时活动".equals(place.category)) continue;
                JSONObject item = new JSONObject();
                item.put("name", place.name);
                item.put("time", place.openTime);
                item.put("venue", place.floors);
                item.put("description", place.description);
                item.put("latitude", place.coordinate.latitude);
                item.put("longitude", place.coordinate.longitude);
                items.put(item);
            }
            SharedPreferences prefs = getSharedPreferences(ACTIVITY_STORAGE, MODE_PRIVATE);
            String serialized = items.toString();
            boolean committed = prefs.edit().putString(ACTIVITY_STORAGE_KEY, serialized).commit();
            boolean verified = committed && serialized.equals(
                    prefs.getString(ACTIVITY_STORAGE_KEY, null));
            android.util.Log.i("NjuptActivity", "保存活动数=" + items.length()
                    + "，写入成功=" + verified);
            return verified;
        } catch (JSONException error) {
            android.util.Log.e("NjuptActivity", "保存活动失败", error);
            return false;
        }
    }

    /**
     * Unified app-owned dialog shell. New confirmation/input dialogs should use this method
     * so they automatically share the NJUPT blue visual system.
     */
    private AlertDialog showStyledDialog(
            String title,
            String subtitle,
            String iconGlyph,
            View content,
            String confirmText,
            DialogAction onConfirm
    ) {
        LinearLayout card = vertical(22, 22, 22, 20);
        card.setBackground(roundBackground(COLOR_SURFACE, 20));
        card.setElevation(dp(12));

        LinearLayout header = horizontal(0, 0, 0, 0);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = text(iconGlyph, 26, COLOR_PRIMARY);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundBackground(COLOR_PRIMARY_SOFT, 14));
        header.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout heading = vertical(0, 0, 0, 0);
        TextView titleView = text(title, 20, COLOR_PRIMARY_DARK);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        heading.addView(titleView);
        TextView subtitleView = text(subtitle, 13, COLOR_TEXT_SECONDARY);
        subtitleView.setPadding(0, dp(4), 0, 0);
        heading.addView(subtitleView);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, -2, 1);
        headingParams.setMargins(dp(14), 0, dp(8), 0);
        header.addView(heading, headingParams);

        TextView close = text("×", 28, COLOR_TEXT_SECONDARY);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭弹窗");
        header.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        card.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, -2);
        contentParams.setMargins(0, dp(22), 0, 0);
        card.addView(content, contentParams);

        LinearLayout actions = horizontal(0, 0, 0, 0);
        Button cancel = actionButton("取消", COLOR_SURFACE);
        cancel.setTextColor(color(COLOR_PRIMARY));
        Button confirm = actionButton(confirmText, COLOR_PRIMARY);
        confirm.setTextSize(15);

        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        actions.addView(cancel, actionParams);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        confirmParams.setMargins(dp(12), 0, 0, 0);
        actions.addView(confirm, confirmParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.setMargins(0, dp(20), 0, 0);
        card.addView(actions, actionsParams);

        FrameLayout shell = new FrameLayout(this);
        shell.setPadding(dp(8), dp(8), dp(8), dp(8));
        shell.addView(card, new FrameLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(shell)
                .create();
        dialog.setCanceledOnTouchOutside(true);
        close.setOnClickListener(view -> dialog.dismiss());
        cancel.setOnClickListener(view -> dialog.dismiss());
        confirm.setOnClickListener(view -> {
            if (onConfirm.run(dialog)) {
                dialog.dismiss();
            }
        });
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window == null) {
                return;
            }
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.34f;
            window.setAttributes(attributes);
            window.setLayout(
                    Math.round(getResources().getDisplayMetrics().widthPixels * 0.90f),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        });
        dialog.show();
        return dialog;
    }

    private LinearLayout dialogField(String label, EditText input) {
        LinearLayout field = vertical(0, 0, 0, 0);
        TextView labelView = text(label, 13, COLOR_PRIMARY_DARK);
        labelView.setTypeface(labelView.getTypeface(), android.graphics.Typeface.BOLD);
        labelView.setPadding(dp(2), 0, 0, dp(7));
        field.addView(labelView);
        field.addView(input, new LinearLayout.LayoutParams(-1, dp(54)));
        return field;
    }

    private EditText dialogInput(String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setTextColor(color(COLOR_TEXT));
        input.setHintTextColor(color("#93A3B5"));
        input.setHint(hint);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(dialogInputBackground(false));
        input.setOnFocusChangeListener((view, hasFocus) ->
                input.setBackground(dialogInputBackground(hasFocus)));
        return input;
    }

    private android.graphics.drawable.GradientDrawable dialogInputBackground(boolean focused) {
        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color("#F8FBFE"));
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(focused ? 2 : 1), color(focused ? COLOR_PRIMARY : COLOR_BORDER));
        return drawable;
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.CHINA);
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
    }

    private void setupLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_REQUEST);
            locationStatus.setText("定位权限待允许 · 暂无当前位置");
            return;
        }
        startBaiduLocation();
    }

    private void setupDirectionSensor() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
    }

    private void updateCurrentPointOnMap() {
        if (mapView != null && hasValidLocation) {
            mapView.setCurrentPoint(currentPoint, currentAccuracy, currentDirection);
        }
    }

    private void startBaiduLocation() {
        try {
            android.util.Log.i("NjuptLocation", "初始化百度定位服务");
            locationClient = new LocationClient(getApplicationContext());
            LocationClientOption option = new LocationClientOption();
            option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
            option.setCoorType("bd09ll");
            option.setScanSpan(2000);
            option.setOpenGps(true);
            option.setLocationNotify(true);
            // Continuous scan provides periodic fixes even without a detected movement.
            option.setIsNeedAddress(false);
            locationClient.setLocOption(option);
            locationClient.registerLocationListener(locationListener);
            locationClient.start();
            lastLocationStartAttemptAt = android.os.SystemClock.elapsedRealtime();
            android.util.Log.i("NjuptLocation", "百度定位服务 start() 已调用");
            locationStatus.setText("百度定位中 · 等待实时位置");
        } catch (Exception error) {
            android.util.Log.e("NjuptLocation", "百度定位初始化失败", error);
            locationClient = null;
            locationStatus.setText("百度定位初始化失败，请检查应用配置");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST) {
            boolean granted = false;
            for (int result : grantResults) {
                granted = granted || result == PackageManager.PERMISSION_GRANTED;
            }
            if (granted) {
                startBaiduLocation();
            } else {
                locationStatus.setText("未授权定位 · 无法使用当前位置规划");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
        // The continuous location client remains active until onDestroy.
        requestFreshCurrentLocation(null);
    }

    @Override
    protected void onPause() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) {
            return;
        }
        float[] rotationMatrix = new float[9];
        float[] orientationValues = new float[3];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        SensorManager.getOrientation(rotationMatrix, orientationValues);
        float azimuth = (float) Math.toDegrees(orientationValues[0]);
        if (azimuth < 0) {
            azimuth += 360f;
        }
        currentDirection = azimuth;
        hasSensorDirection = true;
        updateCurrentPointOnMap();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentLocationStatusTimeoutTask != null) {
            mainHandler.removeCallbacks(currentLocationStatusTimeoutTask);
        }
        cancelLocationRecovery();
        if (locationClient != null) {
            locationClient.unRegisterLocationListener(locationListener);
            locationClient.stop();
        }
        if (routePlanSearch != null) {
            routePlanSearch.destroy();
        }
        if (geoCoder != null) {
            geoCoder.destroy();
        }
        if (weatherSearch != null) {
            weatherSearch.destroy();
            weatherSearch = null;
        }
        if (mapView != null) {
            mapView.onDestroy();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    private boolean isCyclingSelected() {
        return routeMode.getCheckedRadioButtonId() != -1
                && ((RadioButton) findViewById(routeMode.getCheckedRadioButtonId()))
                .getText().toString().contains("骑");
    }

    private String describeLocationType(int type) {
        if (type == BDLocation.TypeGpsLocation) {
            return "GPS";
        }
        if (type == BDLocation.TypeNetWorkLocation) {
            return "网络定位";
        }
        if (type == BDLocation.TypeOffLineLocation) {
            return "离线定位";
        }
        return "定位类型 " + type;
    }

    private TextView text(String value, float size, String color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(color));
        return view;
    }

    private Button actionButton(String value, String backgroundColor) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        button.setTextColor(backgroundColor.equals(COLOR_SURFACE)
                || backgroundColor.equals(COLOR_PRIMARY_SOFT)
                ? color(COLOR_PRIMARY_DARK) : color(COLOR_SURFACE));
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(roundBackground(backgroundColor, 12));
        button.setElevation(dp(1));
        return button;
    }

    private EditText routeInput(String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setTextSize(14);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setTextColor(color(COLOR_TEXT));
        input.setHintTextColor(color("#8A99A8"));
        input.setBackground(roundBackground(COLOR_SURFACE, 12));
        return input;
    }

    private LinearLayout horizontal(int left, int top, int right, int bottom) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(dp(left), dp(top), dp(right), dp(bottom));
        return layout;
    }

    private LinearLayout vertical(int left, int top, int right, int bottom) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(left), dp(top), dp(right), dp(bottom));
        return layout;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
    }

    private android.graphics.drawable.GradientDrawable roundBackground(String color, int radius) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color(color));
        drawable.setCornerRadius(dp(radius));
        boolean filledAction = color.equals(COLOR_PRIMARY)
                || color.equals(COLOR_PRIMARY_DARK)
                || color.equals(COLOR_PRIMARY_MID);
        drawable.setStroke(dp(1), color(filledAction ? color : COLOR_BORDER));
        return drawable;
    }

    private int color(String value) {
        return android.graphics.Color.parseColor(value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
