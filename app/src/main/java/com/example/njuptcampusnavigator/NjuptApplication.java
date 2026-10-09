package com.example.njuptcampusnavigator;

import android.app.Application;
import android.util.Log;

import com.baidu.location.LocationClient;
import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.RequestAuthResultListener;
import com.baidu.mapapi.SDKInitializer;

public final class NjuptApplication extends Application {
    private static final String TAG = "NjuptCampusNavigator";

    private final RequestAuthResultListener authResultListener = (status, message) -> {
        if (status == 0) {
            Log.i(TAG, "百度地图 SDK 授权成功");
        } else {
            Log.e(TAG, "百度地图 SDK 授权失败，status=" + status + ", message=" + message);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        SDKInitializer.setAgreePrivacy(getApplicationContext(), true);
        LocationClient.setAgreePrivacy(true);
        SDKInitializer.setHttpsEnable(true);
        SDKInitializer.addAuthResultListener(authResultListener);
        SDKInitializer.initialize(getApplicationContext());
        SDKInitializer.setCoordType(CoordType.BD09LL);
    }
}
