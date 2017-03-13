package com.usachev.model;

/**
 * Created by Andrey on 17.01.2017.
 */

public class CrossroadAdjacent {

    private final Long adjacent;

    private final int direction;

    private final Long newNode;

    public CrossroadAdjacent(Long adjacent, int direction, Long newNode) {
        super();
        this.adjacent = adjacent;
        this.direction = direction;
        this.newNode = newNode;
    }

    public int getDirection() {
        return direction;
    }

    public Long getAdjacent() {
        return adjacent;
    }

    public Long getNewNode() {
        return newNode;
    }
}
