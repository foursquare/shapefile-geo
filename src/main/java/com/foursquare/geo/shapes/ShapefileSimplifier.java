// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.foursquare.geo.shapes.indexing.CellLocationReference;
import org.geotools.data.AbstractDataStore;
import org.geotools.data.FeatureSource;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.geometry.jts.ReferencedEnvelope;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class ShapefileSimplifier {
  private ShapefileSimplifier() {

  }
  public static void showHelp() {
    System.err.println(
      ShapefileSimplifier.class.getName() + " "
      + "<original-shapefile.shp> "
      + "<simplified-shapefile.shp> "
      + "<attr-name> "
      + "[underscore-separated-levels]"
    );
    System.exit(1);
  }

  public static void main(String[] args) throws IOException {
    if (args.length < 3) {
      showHelp();
    } else {
      String path = args[0];
      String outPath = args[1];
      String attrName = args[2];

      String outPathPrefix = outPath.substring(0, outPath.length() - 3);
      String[] exts = new String[]{"dbf", "fix", "shp", "shx", "png", "prj", "qix"};
      FileSystem fileSys = FileSystems.getDefault();
      for (String ext: exts) {
        Path outFile = fileSys.getPath(outPathPrefix + ext);
        if (Files.exists(outFile)){
          System.out.println("removing " + outFile);
          try {
            Files.delete(outFile);
          } catch (IOException ioe) {
            System.err.println("Failed to delete " + outFile + ": " + ioe.getMessage());
            System.exit(1);
          }
        }
      }
      // Set up the location reference (bounds, crs)
      ShapefileDataStore readDataStore = ShapefileUtils.featureStore(path);
      FeatureSource fs = readDataStore.getFeatureSource();
      ReferencedEnvelope env = fs.getInfo().getBounds();
      CellLocationReference reference = new CellLocationReference(env, new int[] {40, 2, 2, 2});
      Iterable<LabeledGridSimplifier.FeatureEntry> simpleFeatures = LabeledGridSimplifier.simplify(
        reference,
        ShapefileUtils.featureIterator(path),
        attrName
      );
      Map<String, Class<?>> newSchema = new HashMap<String, Class<?>>();
      newSchema.put(attrName, String.class);
      newSchema.put(reference.attributeName(), reference.attributeType());
      AbstractDataStore dataStore = ShapefileUtils.featureStore(fs, outPath, newSchema);
      ShapefileUtils.addFeatures(dataStore, simpleFeatures);
      dataStore.dispose();
      readDataStore.dispose();
    }
  }
}
