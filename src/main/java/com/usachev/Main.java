package com.usachev;

import org.openstreetmap.osmosis.core.container.v0_6.BoundContainer;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.container.v0_6.EntityProcessor;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.RelationContainer;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.OsmUser;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.xml.v0_6.impl.FastXmlParser;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;


/**
 * Created by Andrey on 16.11.2016.
 */
public class Main {

    private static long newId = -1;
    private static String OSM_USERNAME = "ksarkes";
    private static int OSM_USERID = 100501;

    // If you want to load new data on Openstreetmap.org make this value negative and decrease it
    public static long getNewId() {
        return newId++;
    }

    public static long getChangeSetId() {
        return 100500;
    }

    public static void setNewId(long newId) {
        Main.newId = newId + 1;
    }

    public static OsmUser getOsmUser() {
        return new OsmUser(OSM_USERID, OSM_USERNAME);
    }

    public static void main(String[] args) throws IOException, XMLStreamException
    {
        if (args.length < 2) {
            System.out.println("1st argument should be OSM XML file, 2nd argument as output file name");
            return;
        }

        InputStream input;
        try {
            input = new FileInputStream(args[0]);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }

        XMLInputFactory factory = XMLInputFactory.newInstance();
        // configure it to create readers that coalesce adjacent character sections
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        XMLStreamReader r = factory.createXMLStreamReader(input);
        SidewalkProcessor processor = new SidewalkProcessor(args[1]);
        Sink sink = new Sink() {
            @Override
            public void process(EntityContainer entityContainer) {
                entityContainer.process(new EntityProcessor() {
                    @Override
                    public void process(BoundContainer bound) {
                        processor.addBound(bound);
                    }

                    @Override
                    public void process(NodeContainer node) {
                        processor.addNode(node);
                    }

                    @Override
                    public void process(WayContainer way) {
                        processor.addWay(way);
                    }

                    @Override
                    public void process(RelationContainer relation) {
                        processor.addRelation(relation);
                    }
                });
            }

            @Override
            public void complete() {

            }

            @Override
            public void initialize(Map<String, Object> metaData) {

            }

            @Override
            public void release() {

            }
        };

        FastXmlParser fastXmlParser = new FastXmlParser(sink, r, true);
        fastXmlParser.readOsm();

        try {
            processor.process();
        } catch (UnexpectedSidewalkTypeException e) {
            e.printStackTrace();
        }
    }


}