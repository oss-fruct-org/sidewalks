package com.usachev;

import com.usachev.model.CrossroadAdjacent;
import com.usachev.model.StashedNode;

import org.openstreetmap.osmosis.core.container.v0_6.BoundContainer;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.RelationContainer;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.CommonEntityData;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.xml.common.CompressionMethod;
import org.openstreetmap.osmosis.xml.v0_6.XmlWriter;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import javafx.util.Pair;


/**
 * Created by Andrey on 23.12.2016.
 */
@SuppressWarnings("WeakerAccess")
public class SidewalkProcessor {

    private String outputFileName;

    private final static int TO_CROSSROAD = 1;
    private final static int FROM_CROSSROAD = 2;

    private final static int NEAREST_LEFT = 1;
    private final static int NEAREST_RIGHT = 2;

    // hand of the new node by vector {oldNode, crossroad}
    private static final int LEFT = 1;
    private static final int RIGHT = 2;

    private ArrayList<BoundContainer> bounds = new ArrayList<>();
    private ArrayList<NodeContainer> nodes = new ArrayList<>();
    private ArrayList<WayContainer> ways = new ArrayList<>();
    private ArrayList<RelationContainer> relations = new ArrayList<>();
    private ArrayList<WayContainer> sidewalks = new ArrayList<>();

    // For getting node by its id (e.g. in WayNode)
    private HashMap<Long, NodeContainer> nodesMap = new HashMap<>();
    // For getting way by its id
    private HashMap<Long, WayContainer> waysMap = new HashMap<>();
    // For getting ways related to specified node id
    private HashMap<Long, ArrayList<Long>> waysNodesMap = new HashMap<>();

    private HashMap<Long, Long> currentNodeWayMap = new HashMap<>();
    private ArrayList<StashedNode> stashedNodes = new ArrayList<>();
    private HashMap<Long, ArrayList<CrossroadAdjacent>> processedCrossroads = new HashMap<>();

    private ArrayList<WayContainer> newWritableWays = new ArrayList<>();
    private ArrayList<NodeContainer> newWritableNodes = new ArrayList<>();

    private long maxId = 0;

    public SidewalkProcessor(String outputFileName) {
        this.outputFileName = outputFileName;
    }

    public void addBound(BoundContainer boundContainer) {
        long id = boundContainer.getEntity().getId();
        if (id > maxId) maxId = id;
        bounds.add(boundContainer);
    }

    public void addNode(NodeContainer nodeContainer) {
        nodes.add(nodeContainer);
        long id = nodeContainer.getEntity().getId();
        if (id > maxId) maxId = id;
        nodesMap.put(id, nodeContainer);
    }

    public void addWay(WayContainer wayContainer) {
        ways.add(wayContainer);
        long id = wayContainer.getEntity().getId();
        if (id > maxId) maxId = id;
        waysMap.put(id, wayContainer);

        List<WayNode> wayNodes = wayContainer.getEntity().getWayNodes();
        for (WayNode wayNode : wayNodes) {
            long nodeId = wayNode.getNodeId();
            if (waysNodesMap.get(nodeId) == null) {
                ArrayList<Long> waysArray = new ArrayList<>();
                waysArray.add(wayContainer.getEntity().getId());
                waysNodesMap.put(nodeId, waysArray);
            } else {
                waysNodesMap.get(nodeId).add(wayContainer.getEntity().getId());
            }
        }
    }

    public void addRelation(RelationContainer relationContainer) {
        long id = relationContainer.getEntity().getId();
        if (id > maxId) maxId = id;
        relations.add(relationContainer);
    }

    public void process() throws UnexpectedSidewalkTypeException {
        Main.setNewId(maxId);
        ArrayList<WayContainer> containers = new ArrayList<>();
        sidewalks = new ArrayList<>();
        for (WayContainer way : ways) {
            boolean hasFootway = false;
            boolean hasSidewalk = false;
            for (Tag tag : way.getEntity().getTags()) {
                if (tag.getKey().equals("highway") && tag.getValue().equals("footway")) {
                    hasFootway = true;
                }
                if (tag.getKey().equals("sidewalk") && !tag.getValue().equals("none") && !tag.getValue().equals("no")) {
                    hasSidewalk = true;
                }
            }
//            if (!hasFootway)
                containers.add(way);
            if (hasSidewalk) {
                sidewalks.add(way);
            }
        }
        ways = containers;

        for (WayContainer way : sidewalks) {
            ArrayList<WayNode> wayNodes = (ArrayList<WayNode>) way.getEntity().getWayNodes();

            ArrayList<WayNode> wayNodesLeft = new ArrayList<>();
            ArrayList<WayNode> wayNodesRight = new ArrayList<>();

            String sidewalkDirection = determineSidewalk(way);
            if (sidewalkDirection == null)
                throw new UnexpectedSidewalkTypeException();

            for (int i = 0; i < wayNodes.size(); i++) {
                NodeContainer node = nodesMap.get(wayNodes.get(i).getNodeId());

                if (isCrossroad(node)) {
                    Long crossroadNodeId = node.getEntity().getId();
                    Long prevNodeId = null, nextNodeId = null;
                    NodeContainer prevNode = null, nextNode = null;
                    Double initialAngle;

                    if (i > 0) {
                        prevNodeId = wayNodes.get(i - 1).getNodeId();
                        prevNode = nodesMap.get(prevNodeId);
                        initialAngle = GeoUtil.angle(node, prevNode);
                    } else {
                        // i always > 2, so we can do that
                        nextNodeId = wayNodes.get(i + 1).getNodeId();
                        nextNode = nodesMap.get(nextNodeId);
                        initialAngle = GeoUtil.angle(node, nextNode);
                    }

                    Pair<Long, Long> nearests;
                    NodeContainer nearestLeft, nearestRight;

                    if (prevNodeId != null) {
                        nearests = findNearestNodes(node, initialAngle, prevNodeId);
                        nearestLeft = nodesMap.get(nearests.getKey());
                        nearestRight = nodesMap.get(nearests.getValue());
                    }
                    else {
                        // Swap first and second if we use next node to find nearest (reverse vector firstDirection)
                        nearests = findNearestNodes(node, initialAngle, nextNodeId);
                        nearestRight = nodesMap.get(nearests.getKey());
                        nearestLeft = nodesMap.get(nearests.getValue());
                    }

                    Long nearestLeftId = nearestLeft.getEntity().getId();
                    Long nearestRightId = nearestRight.getEntity().getId();

                    String leftSidewalkType = determineCurrentSidewalk(nearestLeft);
                    String rightSidewalkType = determineCurrentSidewalk(nearestRight);
                    String initSidewalkType = determineSidewalk(way);
                    if (initSidewalkType == null)
                        throw new UnexpectedSidewalkTypeException();

                    Long processingNodeId = prevNodeId != null ? prevNodeId : nextNodeId;
                    NodeContainer processingNode = nodesMap.get(processingNodeId);
                    int processingDirection = prevNodeId != null ? TO_CROSSROAD : FROM_CROSSROAD;
                    boolean inverseDirection = processingNodeId.equals(nextNodeId);
                    NodeContainer newNode;
                    NodeContainer newTrailNode;

                    if (initSidewalkType.equals("left") || initSidewalkType.equals("both")) {
                        if (leftSidewalkType != null) {
                            if (shouldUniteSidewalks(node, processingNodeId, nearestLeft, initSidewalkType,
                                    processingDirection, leftSidewalkType, NEAREST_LEFT)) {

                                boolean hasProcessedNode = false;
                                if (processedCrossroads.containsKey(crossroadNodeId)) {
                                    int direction = inverseDirection ? RIGHT : LEFT;
                                    for (CrossroadAdjacent crossroadAdjacent : processedCrossroads.get(crossroadNodeId)) {
                                        if (crossroadAdjacent.getAdjacent().equals(processingNodeId) && crossroadAdjacent.getDirection() == direction) {
                                            wayNodesLeft.add(new WayNode(crossroadAdjacent.getNewNode()));
                                            hasProcessedNode = true;
                                            break;
                                        }
                                    }
                                }

                                if (!hasProcessedNode) {
                                    if (inverseDirection)
                                        newNode = GeoUtil.moveNode(node, nearestLeft, processingNode, GeoUtil.LEFT);
                                    else
                                        newNode = GeoUtil.moveNode(node, processingNode, nearestLeft, GeoUtil.LEFT);
                                    wayNodesLeft.add(new WayNode(newNode.getEntity().getId()));
                                    newWritableNodes.add(newNode);

                                    ArrayList<CrossroadAdjacent> list = new ArrayList<>();
                                    if (processedCrossroads.containsKey(crossroadNodeId))
                                        list = processedCrossroads.get(crossroadNodeId);
                                    list.add(new CrossroadAdjacent(processingNodeId, inverseDirection ? RIGHT : LEFT, newNode.getEntity().getId()));
                                    list.add(new CrossroadAdjacent(nearestLeftId, inverseDirection ? LEFT : RIGHT, newNode.getEntity().getId()));
                                    processedCrossroads.put(
                                            crossroadNodeId,
                                            list);
                                }
                            } else {
                                if (inverseDirection) {
                                    newNode = GeoUtil.moveNode(node, nearestLeft, processingNode, GeoUtil.LEFT);
                                    newTrailNode = GeoUtil.projectNodeToLine(newNode, node, nearestLeft);
                                    wayNodesLeft.add(new WayNode(newTrailNode.getEntity().getId()));
                                    wayNodesLeft.add(new WayNode(newNode.getEntity().getId()));
                                } else {
                                    newNode = GeoUtil.moveNode(node, processingNode, nearestLeft, GeoUtil.LEFT);
                                    newTrailNode = GeoUtil.projectNodeToLine(newNode, node, nearestLeft);
                                    wayNodesLeft.add(new WayNode(newNode.getEntity().getId()));
                                    wayNodesLeft.add(new WayNode(newTrailNode.getEntity().getId()));
                                }

                                int index = getInsertIndex(currentNodeWayMap.get(nearestLeftId), node, nearestLeft);
                                stashedNodes.add(new StashedNode(newTrailNode, currentNodeWayMap.get(nearestLeftId), index));
                                newWritableNodes.add(newNode);
                                newWritableNodes.add(newTrailNode);

                                ArrayList<CrossroadAdjacent> list = new ArrayList<>();
                                if (processedCrossroads.containsKey(crossroadNodeId))
                                    list = processedCrossroads.get(crossroadNodeId);
                                list.add(new CrossroadAdjacent(processingNodeId, inverseDirection ? RIGHT : LEFT, newNode.getEntity().getId()));
                                processedCrossroads.put(
                                        crossroadNodeId,
                                        list);
                            }
                        } else {
                            if (inverseDirection) {
                                newNode = GeoUtil.moveNode(node, nearestLeft, processingNode, GeoUtil.LEFT);
                                newTrailNode = GeoUtil.projectNodeToLine(newNode, node, nearestLeft);
                                wayNodesLeft.add(new WayNode(newTrailNode.getEntity().getId()));
                                wayNodesLeft.add(new WayNode(newNode.getEntity().getId()));
                            } else {
                                newNode = GeoUtil.moveNode(node, processingNode, nearestLeft, GeoUtil.LEFT);
                                newTrailNode = GeoUtil.projectNodeToLine(newNode, node, nearestLeft);
                                wayNodesLeft.add(new WayNode(newNode.getEntity().getId()));
                                wayNodesLeft.add(new WayNode(newTrailNode.getEntity().getId()));
                            }

                            int index = getInsertIndex(currentNodeWayMap.get(nearestLeftId), node, nearestLeft);
                            stashedNodes.add(new StashedNode(newTrailNode, currentNodeWayMap.get(nearestLeftId), index));
                            newWritableNodes.add(newNode);
                            newWritableNodes.add(newTrailNode);
                        }
                    }

                    if (initSidewalkType.equals("right") || initSidewalkType.equals("both")) {
                        if (rightSidewalkType != null) {
                            if (shouldUniteSidewalks(node, processingNodeId, nearestRight, initSidewalkType,
                                    processingDirection, rightSidewalkType, NEAREST_RIGHT)) {

                                boolean hasProcessedNode = false;

                                if (processedCrossroads.containsKey(crossroadNodeId)) {
                                    int direction = inverseDirection ? LEFT : RIGHT;
                                    for (CrossroadAdjacent crossroadAdjacent : processedCrossroads.get(crossroadNodeId)) {
                                        if (crossroadAdjacent.getAdjacent().equals(processingNodeId) && crossroadAdjacent.getDirection() == direction) {
                                            wayNodesRight.add(new WayNode(crossroadAdjacent.getNewNode()));
                                            hasProcessedNode = true;
                                            break;
                                        }
                                    }
                                }

                                if (!hasProcessedNode) {
                                    if (inverseDirection)
                                        newNode = GeoUtil.moveNode(node, nearestRight, processingNode, GeoUtil.RIGHT);
                                    else
                                        newNode = GeoUtil.moveNode(node, processingNode, nearestRight, GeoUtil.RIGHT);

                                    wayNodesRight.add(new WayNode(newNode.getEntity().getId()));
                                    newWritableNodes.add(newNode);

                                    ArrayList<CrossroadAdjacent> list = new ArrayList<>();
                                    if (processedCrossroads.containsKey(crossroadNodeId))
                                        list = processedCrossroads.get(crossroadNodeId);
                                    list.add(new CrossroadAdjacent(processingNodeId, inverseDirection ? LEFT : RIGHT, newNode.getEntity().getId()));
                                    list.add(new CrossroadAdjacent(nearestRightId, inverseDirection ? RIGHT : LEFT, newNode.getEntity().getId()));
                                    processedCrossroads.put(
                                            crossroadNodeId,
                                            list);
                                }
                            } else {
                                if (inverseDirection) {
                                    newNode = GeoUtil.moveNode(node, nearestRight, processingNode, GeoUtil.RIGHT);
                                    newTrailNode = GeoUtil.projectNodeToLine(newNode, node, nearestRight);
                                    wayNodesRight.add(new WayNode(newTrailNode.getEntity().getId()));
                                    wayNodesRight.add(new WayNode(newNode.getEntity().getId()));
                                } else {
                                    newNode = GeoUtil.moveNode(node, processingNode, nearestRight, GeoUtil.RIGHT);
                                    newTrailNode = GeoUtil.projectNodeToLine(newNode, node, nearestRight);
                                    wayNodesRight.add(new WayNode(newNode.getEntity().getId()));
                                    wayNodesRight.add(new WayNode(newTrailNode.getEntity().getId()));
                                }

                                int index = getInsertIndex(currentNodeWayMap.get(nearestRightId), node, nearestRight);
                                stashedNodes.add(new StashedNode(newTrailNode, currentNodeWayMap.get(nearestRightId), index));
                                newWritableNodes.add(newNode);
                                newWritableNodes.add(newTrailNode);

                                ArrayList<CrossroadAdjacent> list = new ArrayList<>();
                                if (processedCrossroads.containsKey(crossroadNodeId))
                                    list = processedCrossroads.get(crossroadNodeId);
                                list.add(new CrossroadAdjacent(processingNodeId, inverseDirection ? LEFT : RIGHT, newNode.getEntity().getId()));
                                processedCrossroads.put(
                                        crossroadNodeId,
                                        list);
                            }
                        } else {
                            if (inverseDirection) {
                                newNode = GeoUtil.moveNode(node, nearestRight, processingNode, GeoUtil.RIGHT);
                                newTrailNode = GeoUtil.projectNodeToLine(newNode, node, nearestRight);
                                wayNodesRight.add(new WayNode(newTrailNode.getEntity().getId()));
                                wayNodesRight.add(new WayNode(newNode.getEntity().getId()));
                            } else {
                                newNode = GeoUtil.moveNode(node, processingNode, nearestRight, GeoUtil.RIGHT);
                                newTrailNode = GeoUtil.projectNodeToLine(newNode, node, nearestRight);
                                wayNodesRight.add(new WayNode(newNode.getEntity().getId()));
                                wayNodesRight.add(new WayNode(newTrailNode.getEntity().getId()));
                            }

                            int index = getInsertIndex(currentNodeWayMap.get(nearestRightId), node, nearestRight);
                            stashedNodes.add(new StashedNode(newTrailNode, currentNodeWayMap.get(nearestRightId), index));

                            newWritableNodes.add(newNode);
                            newWritableNodes.add(newTrailNode);
                        }
                    }
                    // clear hashmap
                    currentNodeWayMap = new HashMap<>();
                } else {
                    if (sidewalkDirection.equals("both") || sidewalkDirection.equals("left")) {
                        NodeContainer prevNode = null, nextNode = null;
                        if (i > 0)
                            prevNode = nodesMap.get(wayNodes.get(i - 1).getNodeId());
                        if (i < wayNodes.size() - 1)
                            nextNode = nodesMap.get(wayNodes.get(i + 1).getNodeId());
                        NodeContainer newNode = GeoUtil.moveNode(node, prevNode, nextNode, GeoUtil.LEFT);
                        wayNodesLeft.add(new WayNode(newNode.getEntity().getId()));
                        newWritableNodes.add(newNode);
                    }
                    if (sidewalkDirection.equals("both") || sidewalkDirection.equals("right")) {
                        NodeContainer prevNode = null, nextNode = null;
                        if (i > 0)
                            prevNode = nodesMap.get(wayNodes.get(i - 1).getNodeId());
                        if (i < wayNodes.size() - 1)
                            nextNode = nodesMap.get(wayNodes.get(i + 1).getNodeId());
                        NodeContainer newNode = GeoUtil.moveNode(node, prevNode, nextNode, GeoUtil.RIGHT);
                        wayNodesRight.add(new WayNode(newNode.getEntity().getId()));
                        newWritableNodes.add(newNode);
                    }
                }
            }

            Way way1 = new Way(new CommonEntityData(Main.getNewId(), 1, Calendar.getInstance().getTime(), Main.getOsmUser(), Main.getChangeSetId()), wayNodesLeft);
            Way way2 = new Way(new CommonEntityData(Main.getNewId(), 1, Calendar.getInstance().getTime(), Main.getOsmUser(), Main.getChangeSetId()), wayNodesRight);
            way1.getTags().add(new Tag("highway", "footway"));
            way1.getTags().add(new Tag("footway", "sidewalk"));
            way2.getTags().add(new Tag("highway", "footway"));
            way2.getTags().add(new Tag("footway", "sidewalk"));
            newWritableWays.add(new WayContainer(way1));
            newWritableWays.add(new WayContainer(way2));
        }
        insertStashedNodes();
        writeOsmXml();
        writeSidewalks();
    }

    /**
     * Determine sidewalk type of node which belong to appropriate way in currentNodeWayMap
     * Return null if there is no sidewalk
     */
    private String determineCurrentSidewalk(NodeContainer node) {
        Long wayId = currentNodeWayMap.get(node.getEntity().getId());
        for (Tag tag : waysMap.get(wayId).getEntity().getTags()) {
            if (tag.getKey().equals("sidewalk") && (tag.getValue().equals("both") || tag.getValue().equals("left") || tag.getValue().equals("right")))
                return tag.getValue();
        }
        return null;
    }

    private String determineSidewalk(WayContainer way) {
        for(Tag tag : way.getEntity().getTags()) {
            if (tag.getKey().equals("sidewalk") && (tag.getValue().equals("both") || tag.getValue().equals("left") || tag.getValue().equals("right")))
                return tag.getValue();
        }
        return null;
    }

    private boolean shouldUniteSidewalks(NodeContainer crossroad, Long processingNodeId, NodeContainer nearestNode,
                                         String processingSidewalkType, int processingDirection,
                                         String nearestSidewalkType, int nearestNodeHand) {

        Long wayId = currentNodeWayMap.get(processingNodeId);
        int nearestEdgeDirection = edgeDirection(crossroad, nearestNode);

        // TODO: think about conditions
        if (nearestNodeHand == NEAREST_LEFT &&
                (processingSidewalkType.equals("left") || processingSidewalkType.equals("both"))) {
            if (processingDirection == TO_CROSSROAD) {
                if (nearestEdgeDirection == TO_CROSSROAD &&
                        (nearestSidewalkType.equals("right") || nearestSidewalkType.equals("both")))
                    return true;
                else if (nearestEdgeDirection == FROM_CROSSROAD &&
                        (nearestSidewalkType.equals("left") || nearestSidewalkType.equals("both")))
                    return true;
            } else if (processingDirection == FROM_CROSSROAD) {
                if (nearestEdgeDirection == TO_CROSSROAD &&
                        (nearestSidewalkType.equals("left") || nearestSidewalkType.equals("both")))
                    return true;
                else if (nearestEdgeDirection == FROM_CROSSROAD &&
                        (nearestSidewalkType.equals("right") || nearestSidewalkType.equals("both")))
                    return true;
            }
        } else if (nearestNodeHand == NEAREST_RIGHT &&
                (processingSidewalkType.equals("right") || processingSidewalkType.equals("both"))) {
            if (processingDirection == TO_CROSSROAD) {
                if (nearestEdgeDirection == TO_CROSSROAD &&
                        (nearestSidewalkType.equals("left") || nearestSidewalkType.equals("both")))
                    return true;
                else if (nearestEdgeDirection == FROM_CROSSROAD &&
                        (nearestSidewalkType.equals("right") || nearestSidewalkType.equals("both")))
                    return true;
            } else if (processingDirection == FROM_CROSSROAD) {
                if (nearestEdgeDirection == TO_CROSSROAD &&
                        (nearestSidewalkType.equals("right") || nearestSidewalkType.equals("both")))
                    return true;
                else if (nearestEdgeDirection == FROM_CROSSROAD &&
                        (nearestSidewalkType.equals("left") || nearestSidewalkType.equals("both")))
                    return true;
            }
        }

        return false;
    }

    private void insertStashedNodes() {
        for (StashedNode node : stashedNodes) {
            WayContainer way = waysMap.get(node.getWayId());
            way.getEntity().getWayNodes().add(node.getInsertIndex(), new WayNode(node.getNewNode().getEntity().getId()));
        }
    }

    private int getInsertIndex(Long wayId, NodeContainer node1, NodeContainer node2) {
        WayContainer way = waysMap.get(wayId);
        Long prevId = node1.getEntity().getId();
        Long nextId = node2.getEntity().getId();
        ArrayList<WayNode> wayNodes = ((ArrayList<WayNode>) way.getEntity().getWayNodes());
        for (int i = 0; i < wayNodes.size(); i++) {
            if (i < wayNodes.size() - 1 && wayNodes.get(i).getNodeId() == prevId &&
                    wayNodes.get(i + 1).getNodeId() == nextId) {
                return i + 1;
            } else if (i > 0 && wayNodes.get(i).getNodeId() == prevId &&
                    wayNodes.get(i - 1).getNodeId() == nextId)
                return i;
        }
        return -1;
    }

    private int edgeDirection(NodeContainer crossroad, NodeContainer node) {
        Long wayId = currentNodeWayMap.get(node.getEntity().getId());
        List<WayNode> wayNodes = waysMap.get(wayId).getEntity().getWayNodes();
        for (int i = 0; i < wayNodes.size(); i++) {
            if (wayNodes.get(i).getNodeId() == crossroad.getEntity().getId()) {
                if (i > 0 && wayNodes.get(i - 1).getNodeId() == node.getEntity().getId())
                    return TO_CROSSROAD;
                else if (i < wayNodes.size() - 1 && wayNodes.get(i + 1).getNodeId() == node.getEntity().getId())
                    return FROM_CROSSROAD;
            }
        }
        return 0;
    }

    private Pair<Long, Long> findNearestNodes(NodeContainer node, double initialAngle, long unhandledNode) {

        // TODO: think about nextNode processing on crossroads
        ArrayList<Long> adjacents = findAllAdjacentNodes(node);
        Long nearestLeftId = null, nearestRightId = null;
        Double nearestLeftAngle = null, nearestRightAngle = null;

        for (Long adjId : adjacents) {
            if (!adjId.equals(unhandledNode)) {
                NodeContainer currAdjNode = nodesMap.get(adjId);
                double currAngle = GeoUtil.angle(node, currAdjNode);

                // Init it by some values
                if (nearestLeftAngle == null) {
                    nearestLeftAngle = currAngle;
                    nearestLeftId = adjId;
                }
                if (nearestRightAngle == null) {
                    nearestRightAngle = currAngle;
                    nearestRightId = adjId;
                }

                if (initialAngle < 0) {
                    if (nearestRightAngle < initialAngle) {
                        if (currAngle > initialAngle ||
                                currAngle < nearestRightAngle) {
                            nearestRightAngle = currAngle;
                            nearestRightId = adjId;
                        }
                    } else if (currAngle < nearestRightAngle
                            && currAngle > initialAngle) {
                        nearestRightAngle = currAngle;
                        nearestRightId = adjId;
                    }

                    if (nearestLeftAngle < initialAngle) {
                        if (currAngle > nearestLeftAngle
                                && currAngle < initialAngle) {
                            nearestLeftAngle = currAngle;
                            nearestLeftId = adjId;
                        }
                    } else if (currAngle < initialAngle
                            || currAngle > nearestLeftAngle) {
                        nearestLeftAngle = currAngle;
                        nearestLeftId = adjId;
                    }
                } else {
                    if (nearestRightAngle > initialAngle) {
                        if (currAngle > initialAngle
                                && currAngle < nearestRightAngle) {
                            nearestRightAngle = currAngle;
                            nearestRightId = adjId;
                        }
                    } else if (currAngle < nearestRightAngle
                            || currAngle > initialAngle) {
                        nearestRightAngle = currAngle;
                        nearestRightId = adjId;
                    }

                    if (nearestLeftAngle > initialAngle) {
                        if (currAngle > nearestLeftAngle
                                || currAngle < initialAngle) {
                            nearestLeftAngle = currAngle;
                            nearestLeftId = adjId;
                        }
                    } else if (currAngle < initialAngle
                            && currAngle > nearestLeftAngle) {
                        nearestLeftAngle = currAngle;
                        nearestLeftId = adjId;
                    }
                }
            }
        }
        return new Pair<>(nearestLeftId, nearestRightId);
    }

    private ArrayList<Long> findAllAdjacentNodes(NodeContainer node) {
        ArrayList<Long> adjacentNodes = new ArrayList<>();
        for (Long wayId : waysNodesMap.get(node.getEntity().getId())) {
            // Find adjacent nodes to specified node for each intersected way in this crossroad
            ArrayList<WayNode> wayNodes = (ArrayList<WayNode>) waysMap.get(wayId).getEntity().getWayNodes();
            for (int i = 0; i < wayNodes.size(); i++) {
                if (wayNodes.get(i).getNodeId() == node.getEntity().getId()) {
                    if (i > 0) {
                        if (!currentNodeWayMap.containsKey(wayNodes.get(i - 1).getNodeId())) {
                            adjacentNodes.add(wayNodes.get(i - 1).getNodeId());
                            currentNodeWayMap.put(wayNodes.get(i - 1).getNodeId(), wayId);
                        }
                    }
                    if (i < wayNodes.size() - 1) {
                        if (!currentNodeWayMap.containsKey(wayNodes.get(i + 1).getNodeId())) {
                            adjacentNodes.add(wayNodes.get(i + 1).getNodeId());
                            currentNodeWayMap.put(wayNodes.get(i + 1).getNodeId(), wayId);
                        }
                    }
                }
            }
        }
        return adjacentNodes;
    }

    private boolean isCrossroad(NodeContainer node) {
        return waysNodesMap.get(node.getEntity().getId()).size() > 1;
    }

    private void writeOsmXml() {
        XmlWriter xmlWriter = new XmlWriter(new File(outputFileName + ".osm"), CompressionMethod.None);

        for (BoundContainer bound : bounds)
            xmlWriter.process(bound);
        for (NodeContainer node : nodes)
            xmlWriter.process(node);
        for (NodeContainer node : newWritableNodes)
            xmlWriter.process(node);
        for (WayContainer way : ways) {
            Tag tagToRemove = null;
            boolean footwaySidewalk = false, highway = false;
            String name = "";
            for (Tag tag : way.getEntity().getTags()) {
                if (tag.getKey().equals("footway") && tag.getValue().equals("sidewalk"))
                    footwaySidewalk = true;
                if (tag.getKey().equals("highway"))
                    highway = true;
                if (tag.getKey().equals("sidewalk") && (tag.getValue().equals("both") || tag.getValue().equals("right") || tag.getValue().equals("left"))) {
                    tagToRemove = tag;
                }
                if (tag.getKey().equals("name"))
                    name = tag.getValue();
            }

            if (footwaySidewalk && highway)
                log("Probably mutually exclusive tags: wayId=" + way.getEntity().getId() + " " + name);
            if (tagToRemove != null) {
                way.getEntity().getTags().remove(tagToRemove);
                way.getEntity().getTags().add(new Tag("foot", "no"));
            }
            xmlWriter.process(way);
        }
        for (WayContainer way : newWritableWays)
            xmlWriter.process(way);
        for (RelationContainer relation : relations)
            xmlWriter.process(relation);

        xmlWriter.complete();
        xmlWriter.release();
    }

    private void writeSidewalks()  {
        XmlWriter xmlWriter = new XmlWriter(new File("sidewalks3.osm"), CompressionMethod.None);

        for (BoundContainer bound : bounds)
            xmlWriter.process(bound);

        for (NodeContainer node : nodes)
            xmlWriter.process(node);

        for (WayContainer way : sidewalks) {
            xmlWriter.process(way);
        }

        for (NodeContainer node : newWritableNodes)
            xmlWriter.process(node);

        for (WayContainer way : newWritableWays)
            xmlWriter.process(way);

        for (RelationContainer relation : relations)
            xmlWriter.process(relation);

        xmlWriter.complete();
        xmlWriter.release();
    }

    public static void log(String s) {
        System.out.println(s);
    }
    public static void log(double d) {
        System.out.println(String.valueOf(d));
    }
    public static void log(long l) {
        System.out.println(String.valueOf(l));
    }

}
