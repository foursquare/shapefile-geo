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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The counterpart to {@link com.foursquare.geo.shapes.ShapefileSimplifier}. Once a shapefile
 * is simplified, it can be loaded and used for querying.  {@link com.foursquare.geo.shapes.SimplifiedShapefileGeo}
 * shows example usage within an application.
 */
public class SimplifiedShapefileGeo {

  private SimplifiedShapefileGeo() {

  }

  static class IndexedShapefile extends BaseIndexedValues {
    private Map<CellLocation, IndexedValues> cells;
    private CellLocationReference reference;
    public IndexedShapefile(CellLocationReference reference, Map<CellLocation, IndexedValues> cells) {
      this.cells = cells;
      this.reference = reference;
    }

    @Override
    public Object labelForCoordinate(Coordinate coordinate) {
      return locationValues(coordinate).labelForCoordinate(coordinate);
    }

    @Override
    public List<FeatureEntry> colocatedFeatures(Coordinate coordinate) {
      return locationValues(coordinate).colocatedFeatures(coordinate);
    }

    private IndexedValues locationValues(Coordinate coordinate) {
      CellLocation location = CellLocation.fromCoordinate(reference, coordinate);
      while (location != null) {
        IndexedValues indexedValues = cells.get(location);
        if (indexedValues != null) {
          return indexedValues;
        }
        location = location.parent();
      }
      return null;
    }
  }

  static class ShapeIndexedValues extends BaseIndexedValues {
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
    public Object labelForCoordinate(Coordinate coordinate) {
      Geometry coordGeom = ShapefileUtils.GEOMETRY_FACTORY.createPoint(coordinate);
      for (FeatureEntry entry: featureEntries) {
        if (entry.geometry.covers(coordGeom)) {
          return entry.getLabel();
        }
      }
      return null;
    }

    @Override
    public List<FeatureEntry> colocatedFeatures(Coordinate coordinate) {
      return featureEntries;
    }
  }

  static class SingleIndexedValue extends BaseIndexedValues {
    static final SingleIndexedValue NO_VALUE = new SingleIndexedValue(null);
    private Object value;
    public SingleIndexedValue(Object value) {
      this.value = value;
    }
    @Override
    public Object labelForCoordinate(Coordinate coordinate) {
      return value;
    }

    @Override
    public List<FeatureEntry> colocatedFeatures(Coordinate coordinate) {
      return Collections.emptyList();
    }
  }

  /**
   * Loads a simplified Shapefile
   * @param file the location of the file. Can be a resource on the classpath.
   * @param labelAttribute the attribute to return in IndexedValues. Pass the same value used for
   *                       ShapefileSimplifier.
   * @param simplifySingleLabelCells generally should be true.  when false, checking the
   *                                 {@link com.foursquare.geo.shapes.IndexedValues#labelForCoordinate}
   *                                 will always check containment within a feature within that cell.
   *                                 When true, if all features within a cell have the same value,
   *                                 that value will always be returned without checking feature
   *                                 containment.  Pass the same value used for ShapefileSimplifier.
   * @return an representation of the Shapefile that allows testing the labelAttribute value
   * at a certain point.
   * @throws IOException if the file cannot be loaded
   */
  public static IndexedValues load(
     URL file,
     String labelAttribute,
     boolean simplifySingleLabelCells
  ) throws IOException {
    ShapefileDataStore dataStore = ShapefileUtils.featureStore(file);
    SimpleFeatureSource featureSource = dataStore.getFeatureSource();
    // determine the key, index, attribute names, and the number and size of the index levels
    if (featureSource.getSchema().getDescriptor(labelAttribute) == null) {
      dataStore.dispose();
      throw new IOException("Schema has no attribute named \"" + labelAttribute + "\"");
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
      labelAttribute
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

    IndexedValues indexedValues;
    if (simplifySingleLabelCells) {
      Map<CellLocation, IndexedValues> simpleCellMap = new HashMap<CellLocation, IndexedValues>();
      for (Map.Entry<CellLocation, ShapeIndexedValues> entry: cellMap.entrySet()) {
        simpleCellMap.put(entry.getKey(), entry.getValue().simplified());
      }
      indexedValues = new IndexedShapefile(reference, simpleCellMap);
    } else {
      indexedValues = new IndexedShapefile(reference, Collections.<CellLocation, IndexedValues>unmodifiableMap(cellMap));
    }
    // Apply a basic bounding box filter to handle out-of-bounds
    return indexedValues.with(
      new LabelFilters.BoundingBoxFilter(reference.getEnvelope())
    );
  }
}
