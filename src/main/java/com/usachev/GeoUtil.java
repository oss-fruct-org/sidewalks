package com.usachev;


import com.usachev.model.LatLng;
import com.usachev.model.Point;

import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.CommonEntityData;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;

import java.util.Calendar;

import static com.usachev.Main.getChangeSetId;


/**
 * Created by Andrey on 29.03.2016.
 */
@SuppressWarnings("WeakerAccess")
public class GeoUtil {
    private static final double EARTH_RADIUS = 6371000;
    private static final double RADIANS = Math.PI / 180;
    private static final double DEGREES = 180 / Math.PI;

    public static Point toMerkator(NodeContainer nodeContainer) {
        return toMerkator(new LatLng(nodeContainer.getEntity().getLatitude(), nodeContainer.getEntity().getLongitude()));
    }

    public static Point toMerkator(LatLng point) {
        // lng as lambda, lat as phi
        double radlat = point.getLatitude() * Math.PI / 180;
        double radlng = point.getLongitude() * Math.PI / 180;
        return new Point(radlng, Math.log(Math.tan(Math.PI / 4 + radlat / 2)));
    }

    public static LatLng toLatLng(Point p) {
        return toLatLng(p.x, p.y);
    }

    public static LatLng toLatLng(double x, double y) {
        double radlat = 2 * Math.atan(Math.exp(y)) - Math.PI / 2;
        double deglat = radlat * DEGREES;
        double deglng = x * DEGREES;
        return new LatLng(deglat, deglng);
    }

    /**
     * Point projection on the line
     *
     * @param pointToProject point which need to be pointToProject
     * @param point1 start of the line
     * @param point2 end of the line
     */
    public static LatLng projectPointToLine(LatLng pointToProject, LatLng point1, LatLng point2) {
        Point mercProjected = toMerkator(pointToProject);
        Point mercPoint1 = toMerkator(point1);
        Point mercPoint2 = toMerkator(point2);

        double x1 = mercPoint1.x;
        double y1 = mercPoint1.y;
        double x2 = mercPoint2.x;
        double y2 = mercPoint2.y;
        double x3 = mercProjected.x;
        double y3 = mercProjected.y;
        double x4, y4;

        x4 = ((x2 - x1) * (y2 - y1) * (y3 - y1) + x1 * Math.pow(y2 - y1, 2) + x3 * Math.pow(x2 - x1, 2)) / (Math.pow(y2 - y1, 2) + Math.pow(x2 - x1, 2));
        y4 = (y2 - y1) * (x4 - x1) / (x2 - x1) + y1;
        return toLatLng(x4, y4);
    }

    public static NodeContainer projectNodeToLine(NodeContainer nodeToProject, NodeContainer node1, NodeContainer node2) {
        LatLng newPoint = projectPointToLine(new LatLng(nodeToProject.getEntity().getLatitude(), nodeToProject.getEntity().getLongitude()),
                new LatLng(node1.getEntity().getLatitude(), node1.getEntity().getLongitude()),
                new LatLng(node2.getEntity().getLatitude(), node2.getEntity().getLongitude()));
        return new NodeContainer(new Node(new CommonEntityData(Main.getNewId(), 1, Calendar.getInstance().getTime(), Main.getOsmUser(), getChangeSetId()),
                newPoint.getLatitude(), newPoint.getLongitude()));
    }

    public static double distance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
    }

    public static double angle(NodeContainer n1, NodeContainer n2) {
        return angle(new LatLng(n1.getEntity().getLatitude(), n1.getEntity().getLongitude()),
                new LatLng(n2.getEntity().getLatitude(), n2.getEntity().getLongitude()));
    }

    public static double angle(LatLng l1, LatLng l2) {
        return Math.atan2(l1.getLatitude() - l2.getLatitude(), l1.getLongitude() - l2.getLongitude()) * DEGREES;
    }

    /**
     * @param center point geo coordinates
     * @param distance shift in meters
     * @param bearing angle in degrees
     * @return moved point coordinates
     */
    private static LatLng movePoint(LatLng center, double distance, double bearing) {
        double lat1 = center.getLatitude() * RADIANS;
        double lon1 = center.getLongitude() * RADIANS;
        double radbear = bearing * RADIANS;

        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(distance / EARTH_RADIUS) +
                Math.cos(lat1) * Math.sin(distance / EARTH_RADIUS) * Math.cos(radbear));
        double lon2 = lon1 + Math.atan2(Math.sin(radbear) * Math.sin(distance / EARTH_RADIUS) * Math.cos(lat1),
                Math.cos(distance / EARTH_RADIUS) - Math.sin(lat1) * Math.sin(lat2));

        return new LatLng(lat2 * DEGREES, lon2 * DEGREES);
    }

    private static Point orth1(Point vec) {
        return new Point(-vec.y, vec.x);
    }

    private static Point orth2(Point vec) {
        return new Point(vec.y, -vec.x);
    }

    public static final int LEFT = 1;
    public static final int RIGHT = 2;

    // Must have at least one of prevNode/nextNode parameter NonNull
    public static NodeContainer moveNode(NodeContainer node, NodeContainer prevNode, NodeContainer nextNode, int direction) {
        Point p1 = toMerkator(node);
        Point p2 = null;
        Point p3;
        Point bisector;

        if (prevNode != null && nextNode != null) {
            p2 = toMerkator(prevNode);
            p3 = toMerkator(nextNode);

            Point vec1 = new Point(p1.x - p2.x, p1.y - p2.y);
            vec1 = new Point(vec1.x / norm(vec1), vec1.y / norm(vec1));
            Point vec2 = new Point(p3.x - p1.x, p3.y - p1.y);
            vec2 = new Point(vec2.x / norm(vec2), vec2.y / norm(vec2));

            double p = vec1.y * vec2.x - vec1.x * vec2.y;

            if (direction == LEFT) {
                bisector = new Point(p1.x - vec1.x + vec2.x, p1.y - vec1.y + vec2.y);
                if (p > 0) {
                    bisector = new Point(bisector.x - 2 * (bisector.x - p1.x),
                            bisector.y - 2 * (bisector.y - p1.y));
                } else if (p == 0)
                    bisector = orth1(vec1);

            } else {
                bisector = new Point(p1.x - vec1.x + vec2.x, p1.y - vec1.y + vec2.y);
                if (p < 0) {
                    bisector = new Point(bisector.x - 2 * (bisector.x - p1.x),
                            bisector.y - 2 * (bisector.y - p1.y));
                } else if (p == 0)
                    bisector = orth2(vec1);
            }
        } else {
            Point point;
            Point vec;
            if (direction == LEFT) {
                if (prevNode != null) {
                    point = toMerkator(prevNode);
                    vec = new Point(p1.x - point.x, p1.y - point.y);
                } else {
                    point = toMerkator(nextNode);
                    vec = new Point(point.x - p1.x, point.y - p1.y);
                }
                Point orth = orth1(vec);
                bisector = new Point(p1.x + orth.x, p1.y + orth.y);
            } else {
                if (prevNode != null) {
                    point = toMerkator(prevNode);
                    vec = new Point(p1.x - point.x, p1.y - point.y);
                } else {
                    point = toMerkator(nextNode);
                    vec = new Point(point.x - p1.x, point.y - p1.y);
                }
                Point orth = orth2(vec);
                bisector = new Point(p1.x + orth.x, p1.y + orth.y);
            }
        }

        Point p = new Point(-p1.x + bisector.x, -p1.y + bisector.y);
        double ratio = p2 == null ? p.x : p1.x - p2.x;
        ratio = Math.abs(ratio);
        Point newPoint = new Point(p1.x + Math.signum(p.x) * ratio, p1.y + p.y * ratio / Math.abs(p.x));

        LatLng latLng = new LatLng(node.getEntity().getLatitude(), node.getEntity().getLongitude());
        LatLng newStart = movePoint(latLng, 3, azimuth(latLng, toLatLng(newPoint)));
        return new NodeContainer(new Node(new CommonEntityData(Main.getNewId(), 1, Calendar.getInstance().getTime(), Main.getOsmUser(), getChangeSetId()),
                newStart.getLatitude(), newStart.getLongitude()));

    }

    private static double azimuth(LatLng latLng1, LatLng latLng2) {
        double lat1 = latLng1.getLatitude();
        double lon1 = latLng1.getLongitude();
        double lat2 = latLng2.getLatitude();
        double lon2 = latLng2.getLongitude();
        double X = Math.cos(lat2 * RADIANS) * Math.sin(lon2 * RADIANS - lon1 * RADIANS);
        double Y = Math.cos(lat1 * RADIANS) * Math.sin(lat2 * RADIANS) - Math.sin(lat1 * RADIANS)
                * Math.cos(lat2 * RADIANS) * Math.cos(lon2 * RADIANS - lon1 * RADIANS);
        double az = Math.atan2(X, Y) * DEGREES;
        if (az < 0)
            az += 360;
        return az;
    }

    private double dot(Point p1, Point p2) {
        return p1.x * p2.x + p1.y * p2.y;
    }

    private static double norm(Point p) {
        return Math.sqrt(Math.pow(p.x, 2) + Math.pow(p.y, 2));
    }
}
