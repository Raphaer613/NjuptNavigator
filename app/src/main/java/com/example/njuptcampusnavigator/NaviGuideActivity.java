package com.example.njuptcampusnavigator;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.mapapi.bikenavi.BikeNavigateHelper;
import com.baidu.mapapi.bikenavi.adapter.IBEngineInitListener;
import com.baidu.mapapi.bikenavi.adapter.IBRoutePlanListener;
import com.baidu.mapapi.bikenavi.adapter.IBTTSPlayer;
import com.baidu.mapapi.bikenavi.model.BikeRoutePlanError;
import com.baidu.mapapi.bikenavi.params.BikeNaviLaunchParam;
import com.baidu.mapapi.bikenavi.params.BikeRouteNodeInfo;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.walknavi.adapter.IWEngineInitListener;
import com.baidu.mapapi.walknavi.adapter.IWRoutePlanListener;
import com.baidu.mapapi.walknavi.adapter.IWTTSPlayer;
import com.baidu.mapapi.walknavi.model.WalkRoutePlanError;
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

    private static final String TAG = "NjuptNavi";

    private boolean cycling;
    private boolean navigationStarted;
    private BikeNavigateHelper bikeHelper;
    private WalkNavigateHelper walkHelper;
    private TextToSpeech textToSpeech;
    private boolean ttsReady;
    private String pendingSpeech;
    private boolean pendingSpeechPreempt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cycling = getIntent().getBooleanExtra(EXTRA_CYCLING, false);
        showLoading();
        setupTextToSpeech();
        if (cycling) {
            startBikeNavigation();
        } else {
            startWalkNavigation();
        }
    }

    private void startWalkNavigation() {
        walkHelper = WalkNavigateHelper.getInstance();
        walkHelper.initNaviEngine(this, new IWEngineInitListener() {
            @Override
            public void engineInitSuccess() {
                walkHelper.setTTsPlayer(new IWTTSPlayer() {
                    @Override
                    public int playTTSText(String text, boolean preempt) {
                        speakNavigationText(text, preempt);
                        return 0;
                    }
                });
                routeWalk();
            }

            @Override
            public void engineInitFail() {
                showFailure("步行导航引擎初始化失败");
            }
        });
    }

    private void startBikeNavigation() {
        bikeHelper = BikeNavigateHelper.getInstance();
        bikeHelper.initNaviEngine(this, new IBEngineInitListener() {
            @Override
            public void engineInitSuccess() {
                bikeHelper.setTTsPlayer(new IBTTSPlayer() {
                    @Override
                    public int playTTSText(String text, boolean preempt) {
                        speakNavigationText(text, preempt);
                        return 0;
                    }
                });
                routeBike();
            }

            @Override
            public void engineInitFail() {
                showFailure("骑行导航引擎初始化失败");
            }
        });
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
        walkHelper.routePlanWithRouteNode(params, new IWRoutePlanListener() {
            @Override
            public void onRoutePlanStart() {
                Log.i(TAG, "步行导航开始算路");
            }

            @Override
            public void onRoutePlanSuccess() {
                View navigationView = walkHelper.onCreate(NaviGuideActivity.this);
                if (navigationView != null) {
                    setContentView(navigationView);
                }
                navigationStarted = true;
                walkHelper.startWalkNavi(NaviGuideActivity.this);
            }

            @Override
            public void onRoutePlanFail(WalkRoutePlanError error) {
                Log.e("WalkNavi", "算路失败，错误码："+error);
            }
        });
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
        bikeHelper.routePlanWithRouteNode(params, new IBRoutePlanListener() {
            @Override
            public void onRoutePlanStart() {
                Log.i(TAG, "骑行导航开始算路");
            }

            @Override
            public void onRoutePlanSuccess() {
                View navigationView = bikeHelper.onCreate(NaviGuideActivity.this);
                if (navigationView != null) {
                    setContentView(navigationView);
                }
                navigationStarted = true;
                bikeHelper.startBikeNavi(NaviGuideActivity.this);
            }

            @Override
            public void onRoutePlanFail(BikeRoutePlanError error) {
                showFailure("百度地图未找到可用骑行导航路线");
            }
        });
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

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.CHINA);
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;
                if (ttsReady && pendingSpeech != null) {
                    speakNavigationText(pendingSpeech, pendingSpeechPreempt);
                    pendingSpeech = null;
                }
            }
        });
    }

    private void speakNavigationText(String text, boolean preempt) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (textToSpeech == null || !ttsReady) {
            pendingSpeech = text;
            pendingSpeechPreempt = preempt;
            return;
        }
        textToSpeech.speak(
                text,
                preempt ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD,
                null,
                "baidu-navigation"
        );
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
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        if (cycling && bikeHelper != null) {
            bikeHelper.quit();
        } else if (walkHelper != null) {
            walkHelper.quit();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
