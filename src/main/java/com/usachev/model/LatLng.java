package com.usachev.model;

/**
 * Created by Andrey on 23.12.2016.
 */

public class LatLng {
    private double lat;
    private double lon;

    public LatLng(String lat, String lon) {
        this.lat = Double.valueOf(lat);
        this.lon = Double.valueOf(lon);
    }

    public LatLng(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public double getLatitude() {
        return lat;
    }

    public void setLatitude(double lat) {
        this.lat = lat;
    }

    public double getLongitude() {
        return lon;
    }

    public void setLongitude(double lon) {
        this.lon = lon;
    }

    @Override
    public String toString() {
        return this.lat + " " + this.lon;
    }
}
