package com.example.njuptcampusnavigator;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Looper;
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
    private static final String TEST_SPEECH = "导航语音已启动";

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cycling = getIntent().getBooleanExtra(EXTRA_CYCLING, false);
        showLoading();
        setupTextToSpeech();
        try {
            if (getIntent().getBooleanExtra(EXTRA_GUIDE_READY, false)) {
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
                            speakNavigationText(text, preempt);
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
                            speakNavigationText(text, preempt);
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
                    speakNavigationText(text, preempt);
                    return 0;
                }
            });
            View navigationView = walkHelper.onCreate(this);
            if (navigationView == null) {
                throw new IllegalStateException("WalkNavigateHelper.onCreate 返回空 View");
            }
            setContentView(navigationView);
            navigationStarted = true;
            walkHelper.startWalkNavi(this);
            speakNavigationText(TEST_SPEECH, false);
        } catch (RuntimeException error) {
            Log.e(TAG, "步行导航页面启动异常", error);
            showFailure("步行导航页面启动失败：" + shortError(error));
        }
    }

    private void applyWalkDisplayOption() {
        if (walkHelper != null) {
            walkHelper.setWalkNaviDisplayOption(new WalkNaviDisplayOption());
        }
    }

    private void startBikeGuidePage() {
        try {
            bikeHelper = BikeNavigateHelper.getInstance();
            bikeHelper.setTTsPlayer(new IBTTSPlayer() {
                @Override
                public int playTTSText(String text, boolean preempt) {
                    Log.i(TAG, "收到骑行导航播报：" + text);
                    speakNavigationText(text, preempt);
                    return 0;
                }
            });
            View navigationView = bikeHelper.onCreate(this);
            if (navigationView == null) {
                throw new IllegalStateException("BikeNavigateHelper.onCreate 返回空 View");
            }
            setContentView(navigationView);
            navigationStarted = true;
            bikeHelper.startBikeNavi(this);
            speakNavigationText(TEST_SPEECH, false);
        } catch (RuntimeException error) {
            Log.e(TAG, "骑行导航页面启动异常", error);
            showFailure("骑行导航页面启动失败：" + shortError(error));
        }
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
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

    private void speakNavigationText(String text, boolean preempt) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (textToSpeech == null || !ttsReady) {
            Log.w(TAG, "系统TTS未就绪，暂存播报：" + text);
            pendingSpeech = text;
            pendingSpeechPreempt = preempt;
            return;
        }
        Log.i(TAG, "系统TTS开始播报：" + text);
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
