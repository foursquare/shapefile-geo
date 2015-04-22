// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.foursquare.geo.shapes.indexing.CellLocationReference;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.geotools.data.AbstractDataStore;
import org.geotools.data.FeatureSource;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class ShapefileSimplifier {
  private ShapefileSimplifier() {

  }
  private static void showHelp(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp(
      ShapefileSimplifier.class.getName()
        + " original.shp"
        + " simplified.shp"
        + " label-attr",
      options
    );
    System.exit(1);
  }

  public static void main(String[] args) throws IOException {
    System.setProperty("logback.level", "info");
    CommandLineParser parser = new GnuParser();
    Options options = new Options();
    options.addOption(
      OptionBuilder
        .withLongOpt("level-sizes")
        .withDescription("Comma-separated branching factor of grid per level. Default is 40,2,2,2.")
        .hasArg()
        .create()
    );

    options.addOption(
      OptionBuilder
        .withLongOpt("no-geometry-simplification")
        .withDescription("Skips simplification features to rectangle when a cell has features of only one label.")
        .create()
    );

    options.addOption(
      OptionBuilder
        .withLongOpt("water-triangularization")
        .withDescription("Reduces coastline complexity when a cell has  features with more than one label.")
        .create()
    );

    options.addOption("d", "debug", false, "Show debug output.");
    options.addOption("h", "help", false, "Show this message.");

    CommandLine line = null;
    try {
      line = parser.parse(options, args);
    } catch (ParseException pe) {
      System.err.println(pe.getMessage());
      showHelp(options);
    }

    String[] positionalArgs = line.getArgs();
    if (line.hasOption("debug")) {
      System.setProperty("logback.level", "debug");
    }
    // Initialize logger after log level is set.
    Logger logger = LoggerFactory.getLogger(ShapefileSimplifier.class);
    logger.debug("Debug logging enabled");

    if (positionalArgs.length < 3 || line.hasOption("help")) {
      showHelp(options);
    } else {
      String path = positionalArgs[0];
      String outPath = positionalArgs[1];
      String labelAttribute = positionalArgs[2];

      // Parse Options
      int [] levelSizes = new int[] {40, 2, 2, 2};
      if (line.hasOption("level_sizes")) {
        String[] strLevelSizes = line.getOptionValue("level-sizes").split(",");
        levelSizes = new int[strLevelSizes.length];
        for (int i = 0; i < levelSizes.length; ++i) {
          levelSizes[i] = Integer.parseInt(strLevelSizes[i]);
        }
      }
      boolean simplifySingleLabelCells = true;
      if (line.hasOption("no-geometry-simplification")) {
        simplifySingleLabelCells = false;
      }

      boolean waterTriangularization = false;
      if (line.hasOption("water-triangularization")) {
        waterTriangularization = true;
      }


      String outPathPrefix = outPath.substring(0, outPath.length() - 3);
      String[] exts = new String[]{"dbf", "fix", "shp", "shx", "png", "prj", "qix"};
      FileSystem fileSys = FileSystems.getDefault();
      for (String ext: exts) {
        Path outFile = fileSys.getPath(outPathPrefix + ext);
        if (Files.exists(outFile)){
          logger.info("removing {}", outFile);
          try {
            Files.delete(outFile);
          } catch (IOException ioe) {
            logger.error("Failed to delete {}: {}", outFile, ioe.getMessage());
            System.exit(1);
          }
        }
      }
      // Set up the location reference (bounds, crs)
      ShapefileDataStore readDataStore = ShapefileUtils.featureStore(path);
      FeatureSource fs = readDataStore.getFeatureSource();
      ReferencedEnvelope env = fs.getInfo().getBounds();
      CellLocationReference reference = new CellLocationReference(env, levelSizes);
      FeatureEntryFactory featureEntryFactory = new FeatureEntryFactory(reference, labelAttribute);
      List<FeatureEntry> featureEntries = featureEntryFactory.featureEntries(ShapefileUtils.featureIterator(path));
      Iterable<FeatureEntry> simpleFeatures;
      if (waterTriangularization) {
        logger.info("Triangularizing water");
        List<FeatureEntry> weakFeatures = WaterDelaunayTriangulationSimplifier.simplify(
          reference,
          featureEntryFactory,
          featureEntries
        );
        featureEntries.addAll(weakFeatures);
      }
      logger.info("Simplifying features");
      simpleFeatures = LabeledGridSimplifier.simplify(
        reference,
        featureEntries,
        simplifySingleLabelCells
      );
      logger.info("Writing features to {}", outPath);
      Map<String, Class<?>> newSchema = new HashMap<String, Class<?>>();
      newSchema.put(labelAttribute, String.class);
      newSchema.put(reference.attributeName(), reference.attributeType());
      AbstractDataStore dataStore = ShapefileUtils.featureStore(fs, outPath, newSchema);
      ShapefileUtils.addFeatures(dataStore, simpleFeatures);
      dataStore.dispose();
      readDataStore.dispose();
    }
  }
}
