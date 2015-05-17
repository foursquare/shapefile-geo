// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.vividsolutions.jts.geom.Coordinate;

import java.util.List;

/**
 * An interface for holding values that can be
 * accessed by coordinate.  For example, attributes
 * of features loaded from a Shapefile.
 */
public interface IndexedValues {
  /**
   * retrieve a label by coordinate
   * @param coordinate a 2D point covered by the value of interest
   * @return the label, or null if not found
   */
  Object labelForCoordinate(Coordinate coordinate);


  /**
   * The list of features that all reside at the same
   * {@link com.foursquare.geo.shapes.indexing.CellLocation}
   * that is resolved from the coordinate
   * @param coordinate a 2D point indicating the CellLocation of interest
   * @return the list of features within the CellLocation
   */
  List<FeatureEntry> colocatedFeatures(Coordinate coordinate);

  /**
   * Apply a filter to get a filtered IndexedValues instance
   * @param filter the filter to apply
   * @return a new IndexedValues instance, with the filter proxying
   * the input coordinate and the return value
   */
  IndexedValues with(LabelFilter filter);
}
