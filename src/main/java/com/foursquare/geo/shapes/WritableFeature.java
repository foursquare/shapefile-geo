// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import org.opengis.feature.type.Name;

/**
 * Represents a feature that can be written to a Shapefile using
 * {@link com.foursquare.geo.shapes.ShapefileUtils#addFeatures}.
 * {@link com.foursquare.geo.shapes.ShapefileUtils#writableSimpleFeature} can
 * create a WritableFeature from a SimpleFeature.
 */
interface WritableFeature {
  /**
   * The feature's geometry
   * @return a geometry
   */
  public Object getDefaultGeometry();

  /**
   * Accessor for the feature attributes
   * @param name attribute name
   * @return the attribute value, or null,
   * if it doesn't exist.
   */
  public Object getAttribute(Name name);
}
