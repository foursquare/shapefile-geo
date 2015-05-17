// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes.example;

import com.foursquare.geo.shapes.IndexedValues;
import com.foursquare.geo.shapes.SimplifiedShapefileGeo;
import com.vividsolutions.jts.geom.Coordinate;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

/**
 * Shows example usage of a {@link com.foursquare.geo.shapes.SimplifiedShapefileGeo}
 * using a simple commandline query interface
 */
public class SimplifiedShapefileClient {
  private static void usage() {
    String className = SimplifiedShapefileClient.class.getName();
    System.err.println(
       className + " <file.shp> <attributeName>\n" +
       "  example: " + className + " tz_world_simplified.shp TZID"
    );
    System.exit(1);
  }
  public static void main(String[] args) throws MalformedURLException, IOException {
    if (args.length != 2) {
      usage();
    }
    IndexedValues indexedShapes = SimplifiedShapefileGeo.load(
      new File(args[0]).toURI().toURL(),
      args[1],
      false
    );
    Console c = System.console();
    if (c == null) {
      System.err.println("Failed to open a console.");
      System.exit(1);
    }
    String line;
    while ((line = c.readLine("lat,long> " )) != null) {
      String[] llStr = line.split("\\s*,\\s*");
      if (llStr.length == 0) {
        continue;
      }
      Boolean valid = false;
      double lat = 0, lng = 0;
      if (llStr.length ==  2) {
        try {
          lat = Double.parseDouble(llStr[0]);
          lng = Double.parseDouble(llStr[1]);
          valid = true;
        } catch (NumberFormatException nfe) {

        }
      }
      if (valid) {
        System.out.println(indexedShapes.labelForCoordinate(new Coordinate(lng, lat)));
      } else {
        System.err.println("use format: lat,long");
      }
    }
    System.out.println();
  }
}
