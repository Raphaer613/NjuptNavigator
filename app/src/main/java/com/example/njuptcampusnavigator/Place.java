package com.example.njuptcampusnavigator;

import com.baidu.mapapi.model.LatLng;

public final class Place {
    public final String name;
    public final String category;
    public final String openTime;
    public final String floors;
    public final String description;
    public final LatLng coordinate;
    public final boolean temporary;

    public Place(
            String name,
            String category,
            String openTime,
            String floors,
            String description,
            double latitude,
            double longitude
    ) {
        this(name, category, openTime, floors, description, latitude, longitude, false);
    }

    public Place(
            String name,
            String category,
            String openTime,
            String floors,
            String description,
            double latitude,
            double longitude,
            boolean temporary
    ) {
        this.name = name;
        this.category = category;
        this.openTime = openTime;
        this.floors = floors;
        this.description = description;
        this.coordinate = new LatLng(latitude, longitude);
        this.temporary = temporary;
    }
}
