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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;




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
        Options opts = new Options();
        Option oInput = new Option("i", "input", true, "input OSM XML file");
        oInput.setRequired(true);
        opts.addOption(oInput);
        
        Option oOutput = new Option("o", "output", true, "output file name");
        oOutput.setRequired(true);
        opts.addOption(oOutput);
        
        Option oObstacles = new Option("e", "export-points", true, "File name to export obstacles if it required");
        oObstacles.setRequired(false);
        opts.addOption(oObstacles);
        
        Option oUnknownObstacles = new Option("u", "unknown-tags", true, "File name to export points with unknown tags");
        oUnknownObstacles.setRequired(false);
        opts.addOption(oUnknownObstacles);
        
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;
        
        try {
            cmd = parser.parse(opts, args);
        } catch (ParseException ex) { 
            System.out.println(ex.getMessage());
            formatter.printHelp("sidewalks", opts);
            System.exit(1);
            return;
        }
        
        String inputFilePath = cmd.getOptionValue("input");
        String outputFilePath = cmd.getOptionValue("output");
        String obstaclesFilePath = cmd.getOptionValue("export-points");
        String unknownTagsFilePath = cmd.getOptionValue("unknown-tags");
        

        InputStream input;
        try {
            input = new FileInputStream(inputFilePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }

        XMLInputFactory factory = XMLInputFactory.newInstance();
        // configure it to create readers that coalesce adjacent character sections
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        XMLStreamReader r = factory.createXMLStreamReader(input);
        SidewalkProcessor processor = new SidewalkProcessor(outputFilePath);
        processor.enableSearchObstacles(obstaclesFilePath);
        processor.enableSearchUnknownObstacles(unknownTagsFilePath);
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
            public void initialize(Map<String, Object> map) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void complete() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void release() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
        };

        FastXmlParser fastXmlParser = new FastXmlParser(sink, r, true);
        fastXmlParser.readOsm();

        try {
            processor.process();
        } catch (UnexpectedSidewalkTypeException e) {
            e.printStackTrace();
        }
        processor.closeStreams();
    }


}