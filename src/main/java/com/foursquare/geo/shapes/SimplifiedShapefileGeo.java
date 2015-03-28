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

public class SimplifiedShapefileGeo {

  static class IndexedShapefile implements IndexedValues {
    private Map<CellLocation, IndexedValues> cells;
    private CellLocationReference reference;
    public IndexedShapefile(CellLocationReference reference, Map<CellLocation, IndexedValues> cells) {
      this.cells = cells;
      this.reference = reference;
    }

    public Object valueForCoordinate(Coordinate coordinate) {
      CellLocation location = CellLocation.fromCoordinate(reference, coordinate);
      while (location != null) {
        IndexedValues indexedValues = cells.get(location);
        if (indexedValues != null) {
          return indexedValues.valueForCoordinate(coordinate);
        }
        location = location.parent();
      }
      return null;
    }
  }

  static class ShapeIndexedValues implements IndexedValues {
    private List<FeatureEntry> featureEntries;
    public ShapeIndexedValues() {
      this.featureEntries = new ArrayList<FeatureEntry>();
    }

    public void add(FeatureEntry featureEntry) {
      featureEntries.add(featureEntry);
    }

    public IndexedValues simplified() {
      if (featureEntries.isEmpty()) {
        return SingleIndexedValue.NO_VALUE;
      } else if (featureEntries.size() == 1) {
        return new SingleIndexedValue(featureEntries.get(0).getLabel());
      } else {
        return this;
      }
    }
    @Override
    public Object valueForCoordinate(Coordinate coordinate) {
      Geometry coordGeom = ShapefileUtils.GEOMETRY_FACTORY.createPoint(coordinate);
      for (FeatureEntry entry: featureEntries) {
        if (entry.geometry.covers(coordGeom)) {
          return entry.getLabel();
        }
      }
      return null;
    }
  }

  static class SingleIndexedValue implements IndexedValues {
    static final SingleIndexedValue NO_VALUE = new SingleIndexedValue(null);
    private Object value;
    public SingleIndexedValue(Object value) {
      this.value = value;
    }
    @Override
    public Object valueForCoordinate(Coordinate coordinate) {
      return value;
    }
  }

  public static IndexedValues load(
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

    FeatureEntryFactory featureEntryFactory = new FeatureEntryFactory(
      reference,
      keyAttribute
    );

    Map<CellLocation, ShapeIndexedValues> cellMap = new HashMap<CellLocation, ShapeIndexedValues>();
    for (SimpleFeature feature: ShapefileUtils.featureIterator(dataStore)) {
      FeatureEntry featureEntry = featureEntryFactory.featureEntry(feature);
      if (cellMap.get(featureEntry.location) == null) {
        cellMap.put(featureEntry.location, new ShapeIndexedValues());
      }
      cellMap.get(featureEntry.location).add(featureEntry);
    }
    dataStore.dispose();

    if (!keepGeometry) {
      Map<CellLocation, IndexedValues> simpleCellMap = new HashMap<CellLocation, IndexedValues>();
      for (Map.Entry<CellLocation, ShapeIndexedValues> entry: cellMap.entrySet()) {
        simpleCellMap.put(entry.getKey(), entry.getValue().simplified());
      }
      return new IndexedShapefile(reference, simpleCellMap);
    } else {
      return new IndexedShapefile(reference, Collections.<CellLocation, IndexedValues>unmodifiableMap(cellMap));
    }
  }
}
