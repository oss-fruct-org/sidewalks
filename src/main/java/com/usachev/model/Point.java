package com.usachev.model;

/**
 * Created by Andrey on 30.03.2016.
 */
public class Point {

    public double x;
    public double y;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public String toString() {
        return this.x + " " + this.y;
    }
}
