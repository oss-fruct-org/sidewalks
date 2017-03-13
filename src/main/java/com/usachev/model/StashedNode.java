package com.usachev.model;

import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;

/**
 * Created by Andrey on 17.01.2017.
 */

public class StashedNode {

    private Long wayId;

    private int insertIndex;

    private NodeContainer newNode;

    public StashedNode(NodeContainer newNode, Long wayId, int insertIndex) {
        this.newNode = newNode;
        this.wayId = wayId;
        this.insertIndex = insertIndex;
    }

    public Long getWayId() {
        return wayId;
    }

    public int getInsertIndex() {
        return insertIndex;
    }

    public NodeContainer getNewNode() {
        return newNode;
    }
}
