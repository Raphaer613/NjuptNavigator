package com.example.njuptcampusnavigator;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 1001;

    private final List<Place> places = CampusData.defaultPlaces();
    private LatLng currentPoint = CampusData.CAMPUS_CENTER;
    private Place selectedPlace;
    private CampusMapView mapView;
    private EditText searchInput;
    private EditText startInput;
    private EditText endInput;
    private LinearLayout searchResults;
    private TextView locationStatus;
    private TextView detailText;
    private TextView routeSummary;
    private RadioGroup routeMode;
    private LocationClient locationClient;
    private RoutePlanSearch routePlanSearch;
    private GeoCoder geoCoder;
    private TextToSpeech textToSpeech;
    private boolean ttsReady;
    private boolean lastRouteCycling;
    private String lastRouteSummary = "尚未规划路线";
    private int temporaryPlaceIndex = 1;
    private LatLng routeStartPoint;
    private LatLng routeEndPoint;
    private String routeEndName;
    private LatLng pendingRouteStartPoint;
    private LatLng pendingRouteEndPoint;
    private boolean routeReadyForNavigation;

    private interface CoordinateCallback {
        void onResolved(LatLng point, String displayName);
        void onFailure(String message);
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
            if (location == null || location.getLatitude() == 0 || location.getLongitude() == 0) {
                locationStatus.setText("定位失败 · 使用校园中心点");
                return;
            }

            LatLng detectedPoint = new LatLng(location.getLatitude(), location.getLongitude());
            if (CampusData.isInsideCampus(detectedPoint)) {
                currentPoint = detectedPoint;
                locationStatus.setText("百度定位已更新 · " + describeLocationType(location.getLocType()));
            } else {
                currentPoint = CampusData.SOUTH_GATE;
                locationStatus.setText("检测到用户在校园外 · 已将当前位置设为南门");
            }
            mapView.setCurrentPoint(currentPoint, location.getRadius(), location.getDirection());
        }
    };

    private final OnGetRoutePlanResultListener routeListener = new OnGetRoutePlanResultListener() {
        @Override
        public void onGetWalkingRouteResult(WalkingRouteResult result) {
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
        getWindow().setStatusBarColor(android.graphics.Color.rgb(23, 50, 77));
        routePlanSearch = RoutePlanSearch.newInstance();
        routePlanSearch.setOnGetRoutePlanResultListener(routeListener);
        geoCoder = GeoCoder.newInstance();
        geoCoder.setOnGetGeoCodeResultListener(geoCoderListener);
        buildScreen();
        setupTextToSpeech();
        setupLocation();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color("#F4F7FB"));

        LinearLayout header = horizontal(16, 14, 16, 12);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("南邮校园导航", 23, "#17324D");
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        titleStack.addView(title);
        locationStatus = text("百度地图初始化中 · 使用校园中心点", 13, "#6E7C8F");
        titleStack.addView(locationStatus);
        header.addView(titleStack, new LinearLayout.LayoutParams(0, -2, 1));
        Button addActivity = actionButton("+ 活动", "#F28A2E");
        addActivity.setOnClickListener(v -> showAddActivityDialog());
        header.addView(addActivity, wrap());
        root.addView(header);

        LinearLayout searchRow = horizontal(16, 0, 16, 8);
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("搜索校园地点");
        searchInput.setTextSize(15);
        searchInput.setPadding(dp(14), 0, dp(14), 0);
        searchInput.setBackground(roundBackground("#FFFFFF", 12));
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button searchButton = actionButton("搜索", "#2D7FF9");
        searchButton.setOnClickListener(v -> searchPlaces());
        LinearLayout.LayoutParams searchButtonParams = new LinearLayout.LayoutParams(dp(76), dp(48));
        searchButtonParams.setMargins(dp(8), 0, 0, 0);
        searchRow.addView(searchButton, searchButtonParams);
        root.addView(searchRow);

        searchResults = new LinearLayout(this);
        searchResults.setOrientation(LinearLayout.VERTICAL);
        searchResults.setVisibility(View.GONE);
        root.addView(searchResults, new LinearLayout.LayoutParams(-1, -2));

        mapView = new CampusMapView(this);
        mapView.setPlaces(places);
        mapView.setCurrentPoint(currentPoint, 30f, 0f);
        mapView.setOnPlaceClickListener(this::selectPlace);
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-1, 0, 1.6f);
        mapParams.setMargins(dp(16), 0, dp(16), dp(8));
        root.addView(mapView, mapParams);

        LinearLayout routePanel = vertical(16, 10, 16, 10);
        routePanel.setBackground(roundBackground("#FFFFFF", 12));
        TextView routeTitle = text("路线规划", 16, "#17324D");
        routeTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        routePanel.addView(routeTitle);

        LinearLayout startRow = horizontal(0, 6, 0, 0);
        startInput = routeInput("起点，例如：南校门；留空使用当前位置");
        startRow.addView(startInput, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button currentButton = actionButton("当前位置", "#EAF2FF");
        currentButton.setTextColor(color("#2D7FF9"));
        currentButton.setOnClickListener(v -> {
            startInput.setText("当前位置");
            routeStartPoint = currentPoint;
            routeSummary.setText("起点已设为当前位置（校外时自动使用南门）");
        });
        LinearLayout.LayoutParams currentButtonParams = new LinearLayout.LayoutParams(dp(92), dp(46));
        currentButtonParams.setMargins(dp(8), 0, 0, 0);
        startRow.addView(currentButton, currentButtonParams);
        routePanel.addView(startRow);

        endInput = routeInput("终点，例如：图书馆或南京邮电大学仙林校区");
        routePanel.addView(endInput, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout routeLine = horizontal(0, 4, 0, 0);
        routeMode = new RadioGroup(this);
        routeMode.setOrientation(RadioGroup.HORIZONTAL);
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
        routeLine.addView(routeMode, new LinearLayout.LayoutParams(0, -2, 1));
        Button planButton = actionButton("规划路线", "#1DB89A");
        planButton.setOnClickListener(v -> planRoute());
        routeLine.addView(planButton, wrap());
        routePanel.addView(routeLine);

        routeSummary = text("输入起点和终点后规划路线，也可以直接选择校园地点", 13, "#6E7C8F");
        routeSummary.setPadding(0, dp(4), 0, 0);
        routePanel.addView(routeSummary);
        Button navigationButton = actionButton("开始导航", "#17324D");
        navigationButton.setOnClickListener(v -> startNavigation());
        routePanel.addView(navigationButton, new LinearLayout.LayoutParams(-1, dp(42)));
        root.addView(routePanel, new LinearLayout.LayoutParams(-1, -2));

        ScrollView bottomScroll = new ScrollView(this);
        LinearLayout bottom = vertical(16, 10, 16, 18);
        TextView detailTitle = text("地点详情", 16, "#17324D");
        detailTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        bottom.addView(detailTitle);
        detailText = text("点击地图标记或热门地点查看开放时间、楼层和主要功能。", 14, "#526174");
        detailText.setPadding(dp(14), dp(12), dp(14), dp(12));
        detailText.setBackground(roundBackground("#FFFFFF", 12));
        bottom.addView(detailText);

        TextView hotTitle = text("热门地点", 16, "#17324D");
        hotTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams hotTitleParams = new LinearLayout.LayoutParams(-1, -2);
        hotTitleParams.setMargins(0, dp(14), 0, dp(6));
        bottom.addView(hotTitle, hotTitleParams);
        for (Place place : places) {
            if (place.category.equals("教学楼") || place.name.equals("图书馆")
                    || place.name.equals("食堂") || place.name.equals("操场")) {
                bottom.addView(placeButton(place));
            }
        }
        bottomScroll.addView(bottom);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1.05f);
        root.addView(bottomScroll, scrollParams);

        setContentView(root);
    }

    private Button placeButton(Place place) {
        Button button = actionButton(place.name + "   " + place.category, "#FFFFFF");
        button.setTextColor(color("#17324D"));
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
            searchResults.setVisibility(View.GONE);
            Toast.makeText(this, "请输入地点名称", Toast.LENGTH_SHORT).show();
            return;
        }
        int count = 0;
        for (Place place : places) {
            if (place.name.contains(query) || place.category.contains(query)
                    || place.description.contains(query)) {
                searchResults.addView(placeButton(place));
                count++;
            }
        }
        searchResults.setVisibility(View.VISIBLE);
        if (count == 0) {
            TextView empty = text("没有找到相关地点，请尝试搜索“图书馆”或“食堂”。", 13, "#6E7C8F");
            empty.setPadding(dp(16), dp(2), dp(16), dp(10));
            searchResults.addView(empty);
        }
    }

    private void selectPlace(Place place) {
        selectedPlace = place;
        routeEndPoint = place.coordinate;
        routeEndName = place.name;
        routeReadyForNavigation = false;
        if (endInput != null) {
            endInput.setText(place.name);
        }
        mapView.setSelectedPlace(place);
        detailText.setText(
                place.name + "  ·  " + place.category
                        + "\n开放时间  " + place.openTime
                        + "\n楼层信息  " + place.floors
                        + "\n主要功能  " + place.description
                        + "\n百度坐标  " + place.coordinate.latitude + ", " + place.coordinate.longitude
        );
        routeSummary.setText("终点已设为：" + place.name + "    请选择出行方式后规划");
        searchResults.setVisibility(View.GONE);
    }

    private void planRoute() {
        String startText = startInput == null ? "" : startInput.getText().toString().trim();
        String endText = endInput == null ? "" : endInput.getText().toString().trim();
        if (endText.isEmpty()) {
            Toast.makeText(this, "请输入终点", Toast.LENGTH_SHORT).show();
            return;
        }
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
        if (start && (value.isEmpty() || value.equals("当前位置"))) {
            callback.onResolved(currentPoint, "当前位置");
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

    private void showAddActivityDialog() {
        LinearLayout form = vertical(20, 4, 20, 4);
        EditText nameInput = new EditText(this);
        nameInput.setHint("活动名称，例如：校园讲座");
        EditText locationInput = new EditText(this);
        locationInput.setHint("活动地点说明，例如：大学生活动中心");
        form.addView(nameInput, new LinearLayout.LayoutParams(-1, dp(52)));
        form.addView(locationInput, new LinearLayout.LayoutParams(-1, dp(52)));
        new AlertDialog.Builder(this)
                .setTitle("添加临时活动")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String location = locationInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = "临时活动 " + temporaryPlaceIndex;
                    }
                    if (location.isEmpty()) {
                        location = "当前位置附近";
                    }
                    Place activity = new Place(
                            name,
                            "临时活动",
                            "以活动通知为准",
                            "地面活动点",
                            "活动地点：" + location,
                            currentPoint.latitude,
                            currentPoint.longitude,
                            true
                    );
                    temporaryPlaceIndex++;
                    places.add(activity);
                    mapView.setPlaces(places);
                    selectPlace(activity);
                    Toast.makeText(this, "活动地点已添加到当前定位点", Toast.LENGTH_SHORT).show();
                })
                .show();
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
            locationStatus.setText("定位权限待允许 · 使用校园中心点");
            return;
        }
        startBaiduLocation();
    }

    private void startBaiduLocation() {
        try {
            locationClient = new LocationClient(getApplicationContext());
            LocationClientOption option = new LocationClientOption();
            option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
            option.setCoorType("bd09ll");
            option.setScanSpan(3000);
            option.setOpenGps(true);
            option.setIsNeedAddress(true);
            option.setNeedNewVersionRgc(true);
            locationClient.setLocOption(option);
            locationClient.registerLocationListener(locationListener);
            locationClient.start();
            locationStatus.setText("百度定位中 · 使用校园中心点");
        } catch (Exception error) {
            locationStatus.setText("百度定位不可用 · 使用校园中心点");
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
                locationStatus.setText("未授权定位 · 使用校园中心点");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
        button.setTextColor(backgroundColor.equals("#FFFFFF") ? color("#17324D") : color("#FFFFFF"));
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(roundBackground(backgroundColor, 10));
        return button;
    }

    private EditText routeInput(String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setTextSize(14);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(roundBackground("#FFFFFF", 10));
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
        drawable.setStroke(dp(1), color("#DCE4EC"));
        return drawable;
    }

    private int color(String value) {
        return android.graphics.Color.parseColor(value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
