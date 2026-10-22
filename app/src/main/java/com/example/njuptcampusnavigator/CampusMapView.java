package com.example.njuptcampusnavigator;

import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.Overlay;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CampusMapView extends FrameLayout {
    public interface OnPlaceClickListener {
        void onPlaceClick(Place place);
    }

    private final MapView mapView;
    private final BaiduMap baiduMap;
    private final BitmapDescriptor placeIcon;
    private final BitmapDescriptor activityIcon;
    private final BitmapDescriptor selectedIcon;
    private final Map<Marker, Place> markerPlaces = new HashMap<>();
    private OnPlaceClickListener listener;
    private Overlay routeOverlay;
    private Place selectedPlace;

    public CampusMapView(Context context) {
        super(context);
        mapView = new MapView(context);
        baiduMap = mapView.getMap();
        baiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
        baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(CampusData.CAMPUS_CENTER, 16.5f));
        baiduMap.setMyLocationEnabled(true);
        baiduMap.setMyLocationConfiguration(new MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.NORMAL,
                true,
                null
        ));
        baiduMap.setOnMarkerClickListener(marker -> {
            Place place = markerPlaces.get(marker);
            if (place != null && listener != null) {
                listener.onPlaceClick(place);
            }
            return true;
        });

        placeIcon = BitmapDescriptorFactory.fromResource(android.R.drawable.ic_menu_myplaces);
        activityIcon = BitmapDescriptorFactory.fromResource(android.R.drawable.ic_menu_compass);
        selectedIcon = BitmapDescriptorFactory.fromResource(android.R.drawable.ic_menu_mylocation);
        addView(mapView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void setOnPlaceClickListener(OnPlaceClickListener listener) {
        this.listener = listener;
    }

    public void setPlaces(List<Place> places) {
        markerPlaces.clear();
        baiduMap.clear();
        routeOverlay = null;
        for (Place place : places) {
            BitmapDescriptor icon = place.temporary ? activityIcon : placeIcon;
            Marker marker = (Marker) baiduMap.addOverlay(new MarkerOptions()
                    .position(place.coordinate)
                    .icon(icon)
                    .title(place.name)
                    .zIndex(place.temporary ? 8 : 5));
            if (marker != null) {
                markerPlaces.put(marker, place);
            }
        }
        if (selectedPlace != null) {
            setSelectedPlace(selectedPlace);
        }
    }

    public void setSelectedPlace(Place place) {
        selectedPlace = place;
        for (Map.Entry<Marker, Place> entry : markerPlaces.entrySet()) {
            BitmapDescriptor icon = entry.getValue() == place
                    ? selectedIcon
                    : entry.getValue().temporary ? activityIcon : placeIcon;
            entry.getKey().setIcon(icon);
        }
        baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(place.coordinate, 17.5f));
    }

    public void setCurrentPoint(LatLng point, float accuracy, float direction) {
        MyLocationData locationData = new MyLocationData.Builder()
                .accuracy(accuracy)
                .direction(direction)
                .latitude(point.latitude)
                .longitude(point.longitude)
                .build();
        baiduMap.setMyLocationData(locationData);
    }

    public void setRoute(List<LatLng> points, boolean cycling) {
        if (routeOverlay != null) {
            routeOverlay.remove();
        }
        if (points == null || points.size() < 2) {
            routeOverlay = null;
            return;
        }
        routeOverlay = baiduMap.addOverlay(new PolylineOptions()
                .points(new ArrayList<>(points))
                .width(12)
                .color(cycling ? Color.rgb(242, 138, 46) : Color.rgb(45, 127, 249))
                .zIndex(20));
        baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(points.get(points.size() - 1), 17.2f));
    }

    public void onResume() {
        mapView.onResume();
    }

    public void onPause() {
        mapView.onPause();
    }

    public void onDestroy() {
        baiduMap.setMyLocationEnabled(false);
        placeIcon.recycle();
        activityIcon.recycle();
        selectedIcon.recycle();
        mapView.onDestroy();
    }
}
