// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.foursquare.geo.shapes.indexing.CellLocation;
import com.foursquare.geo.shapes.indexing.CellLocationReference;
import com.vividsolutions.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import java.util.AbstractMap;
import java.util.Map;

class FeatureEntryFactory {
  private String labelAttribute;
  private CellLocation initialLocation;
  private CellLocationReference reference;
  public FeatureEntryFactory(CellLocationReference reference, String labelAttribute) {
    this.labelAttribute = labelAttribute;
    this.reference = reference;
    this.initialLocation = new CellLocation(reference);
  }

  public FeatureEntry featureEntry(SimpleFeature feature) {
    Geometry geom = (Geometry) feature.getDefaultGeometry();
    CellLocation location = maybeLocationFromFeature(feature);
    Map.Entry<String, Object> labelEntry = new AbstractMap.SimpleImmutableEntry<String, Object>(
      labelAttribute,
      feature.getAttribute(labelAttribute)
    );
    return new FeatureEntry(location, labelEntry, geom);
  }

  private CellLocation maybeLocationFromFeature(SimpleFeature feature) {
    Object attrValueOpt = feature.getAttribute(reference.attributeName());
    if (attrValueOpt == null) {
      return initialLocation;
    } else {
      return CellLocation.fromAttributeValue(reference, attrValueOpt);
    }
  }
}
