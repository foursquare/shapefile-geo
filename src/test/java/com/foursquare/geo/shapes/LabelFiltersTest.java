// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;


import com.foursquare.geo.shapes.indexing.CellLocationReference;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.junit.Assert;
import org.junit.Test;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;


public class LabelFiltersTest {
  static final CoordinateReferenceSystem CRS = DefaultGeographicCRS.WGS84;
  static final double DefaultEnvMinX = 0, DefaultEnvMaxX = 10;
  static final double DefaultEnvMinY = 0, DefaultEnvMaxY = 10;
  static final ReferencedEnvelope DefaultEnv = new ReferencedEnvelope(
    DefaultEnvMinX,
    DefaultEnvMaxX,
    DefaultEnvMinY,
    DefaultEnvMaxY,
    CRS
  );
  static final int DefaultCellSize = 2;
  static final CellLocationReference DefaultReference = new CellLocationReference(
    DefaultEnv,
    new int[] { DefaultCellSize, DefaultCellSize }
  );

  /* Layout of Geom1 and Geom2 within envelope:
    -----------10
    |    ***** |
    |    ***** |
    |    **2** |
    Y    ***** |
    |    ***** |
    | ***      |
    | *1*      |
    | ***      |
    0-----X-----
   */
  private Geometry getGeom1() {
    // A 2x2 geometry with centroid 2,2
    return DefaultReference.getGeometryFactory().toGeometry(new ReferencedEnvelope(
      DefaultEnvMinX + 1,
      DefaultEnvMinX + 3,
      DefaultEnvMinY + 1,
      DefaultEnvMinY + 3,
      CRS
    ));
  }

  private Geometry getGeom2() {
    // A 5x5 geometry with centroid 7.5 ,7.5
    return DefaultReference.getGeometryFactory().toGeometry(new ReferencedEnvelope(
      DefaultEnvMinX + 4,
      DefaultEnvMinX + 9,
      DefaultEnvMinY + 4,
      DefaultEnvMinY + 9,
      CRS
    ));
  }


  private Set<Object> timezoneValidLabels() {
    Set<Object> validTimezones = new HashSet<Object>();
    for (String timezoneId: TimeZone.getAvailableIDs()) {
      validTimezones.add(timezoneId);
    }
    return validTimezones;
  }

  private class TestIndexedValues extends BaseIndexedValues {
    public List<FeatureEntry> featureEntries;
    public Object labelForCoordinate;
    @Override
    public List<FeatureEntry> colocatedFeatures(Coordinate coordinate) {
      return featureEntries;
    }
    @Override
    public Object labelForCoordinate(Coordinate coordinate) {
      return labelForCoordinate;
    }
  }

  @Test
  public void testValidLabelFilter() {
    TestIndexedValues testIndexedValues = new TestIndexedValues();
    Object defaultValue = null;
    IndexedValues validIndexedValues =  testIndexedValues.with(
      new LabelFilters.ValidLabelFilter(timezoneValidLabels(), defaultValue)
    );

    testIndexedValues.labelForCoordinate = "America/New_York";
    Assert.assertEquals(
      "Valid values let through",
      testIndexedValues.labelForCoordinate,
      validIndexedValues.labelForCoordinate(new Coordinate(0, 0))
    );

    testIndexedValues.labelForCoordinate = "America/BAD";
    Assert.assertEquals(
      "Non-valid values become default",
      defaultValue,
      validIndexedValues.labelForCoordinate(new Coordinate(0, 0))
    );
  }

  @Test
  public void testTimezoneFilter() {
    TestIndexedValues testIndexedValues = new TestIndexedValues();
    Object defaultValue = null;
    IndexedValues indexedValues =  testIndexedValues.with(
      new LabelFilters.TimezoneLabelFilter()
    );

    testIndexedValues.labelForCoordinate = "America/New_York";
    Assert.assertEquals(
      "Valid values let through",
      testIndexedValues.labelForCoordinate,
      indexedValues.labelForCoordinate(new Coordinate(0, 0))
    );

    testIndexedValues.labelForCoordinate = null;
    Assert.assertEquals(
      "Non-valid values converted to Etc timezone",
      "Etc/GMT0",
      indexedValues.labelForCoordinate(new Coordinate(0, 0))
    );

    Assert.assertEquals(
      "Non-valid values converted to Etc timezone",
      "Etc/GMT-1",
      indexedValues.labelForCoordinate(new Coordinate(15, 0))
    );

    Assert.assertEquals(
      "Non-valid values converted to Etc timezone",
      "Etc/GMT+1",
      indexedValues.labelForCoordinate(new Coordinate(-15, 0))
    );
  }

  @Test
  public void testDefaultLabelFilter() {
    TestIndexedValues testIndexedValues = new TestIndexedValues();
    Object defaultValue = "XX";
    IndexedValues indexedValues = testIndexedValues.with(
      new LabelFilters.DefaultLabelFilter(defaultValue)
    );

    testIndexedValues.labelForCoordinate = "America/New_York";
    Assert.assertEquals(
      "Valid values let through",
      testIndexedValues.labelForCoordinate,
      indexedValues.labelForCoordinate(new Coordinate(0, 0))
    );

    testIndexedValues.labelForCoordinate = null;
    Assert.assertEquals(
      "Non-valid values become default",
      defaultValue,
      indexedValues.labelForCoordinate(new Coordinate(0, 0))
    );
  }

  @Test
  public void testBoundingBoxFilter() {
    TestIndexedValues testIndexedValues = new TestIndexedValues();
    IndexedValues validIndexedValues = testIndexedValues.with(
      new LabelFilters.BoundingBoxFilter(DefaultEnv)
    );

    testIndexedValues.labelForCoordinate = "America/New_York";
    Assert.assertEquals(
      "In-bounds queries let through",
      testIndexedValues.labelForCoordinate,
      validIndexedValues.labelForCoordinate(
        new Coordinate(DefaultEnvMaxX / 2, DefaultEnvMaxY / 2)
      )
    );

    Assert.assertEquals(
      "Border queries let through",
      testIndexedValues.labelForCoordinate,
      validIndexedValues.labelForCoordinate(new Coordinate(DefaultEnvMinX, DefaultEnvMinY))
    );

    Assert.assertEquals(
      "Border queries let through",
      testIndexedValues.labelForCoordinate,
      validIndexedValues.labelForCoordinate(new Coordinate(DefaultEnvMaxX, DefaultEnvMaxY))
    );

    Assert.assertNull(
      "Out of bounds queries return null",
      validIndexedValues.labelForCoordinate(new Coordinate(DefaultEnvMinX - 1, DefaultEnvMinY))
    );
  }

  private List<FeatureEntry> featureEntriesGeom1Geom2() {
    FeatureEntryFactory featureEntryFactory = new FeatureEntryFactory(
      DefaultReference,
      "key"
    );
    List<FeatureEntry> featureEntries = Arrays.asList(
      featureEntryFactory.featureEntry("geom1", false, getGeom1()),
      featureEntryFactory.featureEntry("geom2", false, getGeom2())
    );

    return featureEntries;
  }

  @Test
  public void testCentroidDistanceFilter() {
    List<FeatureEntry> featureEntries = featureEntriesGeom1Geom2();
    TestIndexedValues testIndexedValues = new TestIndexedValues();
    IndexedValues indexedValues = testIndexedValues.with(
      new LabelFilters.FeatureCentroidDistanceFilter(DefaultReference.getGeometryFactory())
    );

    testIndexedValues.featureEntries = featureEntries;
    testIndexedValues.labelForCoordinate = "America/New_York";
    Assert.assertEquals(
      "Valid values let through",
      testIndexedValues.labelForCoordinate,
      indexedValues.labelForCoordinate(
        new Coordinate(0, 0)
      )
    );

    // Now, do calculations
    testIndexedValues.labelForCoordinate = null;
    Assert.assertEquals(
      "3,3 should be closer to geom1 (centroid 2,2) than geom2 (centroid 7.5, 7.5)",
      "geom1",
      indexedValues.labelForCoordinate(
        new Coordinate(3, 3)
      )
    );

    Assert.assertEquals(
      "5,5 should be closer to geom2 (centroid 7.5, 7.5) than geom1 (centroid 2,2)",
      "geom2",
      indexedValues.labelForCoordinate(
        new Coordinate(5, 5)
      )
    );

    Assert.assertEquals(
      "5,3 should be closer to geom1 (centroid 2,2) than geom2 (centroid 7.5, 7.5)",
      "geom1",
      indexedValues.labelForCoordinate(
        new Coordinate(5, 3)
      )
    );
  }

  @Test
  public void testFeatureDistanceFilter() {
    List<FeatureEntry> featureEntries = featureEntriesGeom1Geom2();
    TestIndexedValues testIndexedValues = new TestIndexedValues();
    IndexedValues indexedValues = testIndexedValues.with(
      new LabelFilters.FeatureDistanceFilter(DefaultReference.getGeometryFactory())
    );

    testIndexedValues.featureEntries = featureEntries;
    testIndexedValues.labelForCoordinate = "America/New_York";
    Assert.assertEquals(
      "Valid values let through",
      testIndexedValues.labelForCoordinate,
      indexedValues.labelForCoordinate(
        new Coordinate(0, 0)
      )
    );

    // Now, do calculations
    testIndexedValues.labelForCoordinate = null;
    Assert.assertEquals(
      "3,3 should be closer to geom1 than geom2",
      "geom1",
      indexedValues.labelForCoordinate(
        new Coordinate(3, 3)
      )
    );

    Assert.assertEquals(
      "5,5 should be closer to geom2 than geom1",
      "geom2",
      indexedValues.labelForCoordinate(
        new Coordinate(5, 5)
      )
    );

    // Note that this differs from FeatureCentroidDistance for 5, 3
    Assert.assertEquals(
      "5,3 should be closer to geom2 than geom1",
      "geom2",
      indexedValues.labelForCoordinate(
        new Coordinate(5, 3)
      )
    );
  }

}
