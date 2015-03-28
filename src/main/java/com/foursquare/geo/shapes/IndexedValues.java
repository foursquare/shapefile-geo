// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.vividsolutions.jts.geom.Coordinate;

/**
 * An interface for holding values that can be
 * accessed by coordinate.  For example, attributes
 * of features loaded from a Shapefile.
 */
public interface IndexedValues {
  /**
   * retrieve a value by coordinate
   * @param coordinate a 2D point covered by the value of interest
   * @return the value, or null if not found
   */
  public Object valueForCoordinate(Coordinate coordinate);
}
