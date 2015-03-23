// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.foursquare.geo.shapes.indexing.CellLocation;
import com.foursquare.geo.shapes.indexing.CellLocationReference;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureSource;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.AttributeDescriptor;

import java.io.IOException;
import java.net.URL;
import java.util.*;

class SimplifiedShapefileGeo {
  static interface Cell {
    public Object valueForCoordinate(Coordinate coordinate);
  }

  static class IndexedShapefile implements Cell {
    private Map<CellLocation, Cell> cells;
    private CellLocationReference reference;
    public IndexedShapefile(CellLocationReference reference, Map<CellLocation, Cell> cells) {
      this.cells = cells;
      this.reference = reference;
    }

    public Object valueForCoordinate(Coordinate coordinate) {
      CellLocation location = CellLocation.fromCoordinate(reference, coordinate);
      while (location != null) {
        System.out.println("location for coord: " + coordinate + ": " + location);
        Cell cell = cells.get(location);
        if (cell != null) {
          System.out.println("found.");
          return cell.valueForCoordinate(coordinate);
        }
        location = location.parent();
      }
      return null;
    }
  }

  static class ShapeCell implements Cell {
    private List<LabeledGridSimplifier.FeatureEntry> featureEntries;
    public ShapeCell() {
      this.featureEntries = new ArrayList<LabeledGridSimplifier.FeatureEntry>();
    }

    public void add(LabeledGridSimplifier.FeatureEntry featureEntry) {
      featureEntries.add(featureEntry);
    }

    public Cell simplified() {
      if (featureEntries.isEmpty()) {
        return SingleValueCell.NO_VALUE;
      } else if (featureEntries.size() == 1) {
        return new SingleValueCell(featureEntries.get(0).getLabel());
      } else {
        return this;
      }
    }
    @Override
    public Object valueForCoordinate(Coordinate coordinate) {
      System.out.println("ShapeCell value for coord: " + coordinate);
      Geometry coordGeom = ShapefileUtils.GEOMETRY_FACTORY.createPoint(coordinate);
      for (LabeledGridSimplifier.FeatureEntry entry: featureEntries) {
        if (entry.geometry.covers(coordGeom)) {
          return entry.getLabel();
        }
      }
      return null;
    }
  }

  static class SingleValueCell implements Cell {
    static final SingleValueCell NO_VALUE = new SingleValueCell(null);
    private Object value;
    public SingleValueCell(Object value) {
      this.value = value;
    }
    @Override
    public Object valueForCoordinate(Coordinate coordinate) {
      return value;
    }
  }

  public static Cell load(
     URL file,
     String keyAttribute,
     boolean keepGeometry
  ) throws IOException {
    ShapefileDataStore dataStore = ShapefileUtils.featureStore(file);
    SimpleFeatureSource featureSource = dataStore.getFeatureSource();
    // determine the key, index, attribute names, and the number and size of the index levels
    if (featureSource.getSchema().getDescriptor(keyAttribute) == null) {
      dataStore.dispose();
      throw new IOException("Schema has no attribute named \"" + keyAttribute + "\"");
    }

    CellLocationReference reference = null;

    for (AttributeDescriptor descriptor: featureSource.getSchema().getAttributeDescriptors()) {
      if (descriptor.getLocalName().startsWith(CellLocationReference.AttributePrefix)) {
        reference = CellLocationReference.fromAttributeName(featureSource.getBounds(), descriptor.getLocalName());
      }
    }

    if (reference == null) {
      dataStore.dispose();
      throw new IOException("Schema has no attribute starting with \"" + CellLocationReference.AttributePrefix + "\"");
    }

    LabeledGridSimplifier.FeatureEntryFactory featureEntryFactory = new LabeledGridSimplifier.FeatureEntryFactory(
      reference,
      keyAttribute
    );

    Map<CellLocation, ShapeCell> cellMap = new HashMap<CellLocation, ShapeCell>();
    for (SimpleFeature feature: ShapefileUtils.featureIterator(dataStore)) {
      LabeledGridSimplifier.FeatureEntry featureEntry = featureEntryFactory.featureEntry(feature);
      if (cellMap.get(featureEntry.location) == null) {
        cellMap.put(featureEntry.location, new ShapeCell());
      }
      cellMap.get(featureEntry.location).add(featureEntry);
    }
    dataStore.dispose();

    if (!keepGeometry) {
      Map<CellLocation, Cell> simpleCellMap = new HashMap<CellLocation, Cell>();
      for (Map.Entry<CellLocation, ShapeCell> entry: cellMap.entrySet()) {
        simpleCellMap.put(entry.getKey(), entry.getValue().simplified());
      }
      return new IndexedShapefile(reference, simpleCellMap);
    } else {
      return new IndexedShapefile(reference, Collections.<CellLocation, Cell>unmodifiableMap(cellMap));
    }
  }
}
