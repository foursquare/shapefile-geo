// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.vividsolutions.jts.geom.Coordinate;

/**
 * An interface for building filters for the returned value
 * @see IndexedValues#with(LabelFilter)
 */
public interface LabelFilter {
  /**
   *
   * @param coordinate the input coordinate
   * @param next the next filter to call
   * @return the new label, based on the input coordinate and the returned label
   */
  Object filterLabelForCoordinate(Coordinate coordinate, IndexedValues next);
}
