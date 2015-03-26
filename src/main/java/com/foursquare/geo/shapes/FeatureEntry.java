// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.foursquare.geo.shapes.indexing.CellLocation;
import com.vividsolutions.jts.geom.Geometry;
import org.opengis.feature.type.Name;

import java.util.Map;

class FeatureEntry implements ShapefileUtils.WritableFeature {
  public CellLocation location;
  private Map.Entry<String, Object> labelEntry;
  public Geometry geometry;
  public FeatureEntry(CellLocation location, Map.Entry<String, Object> labelEntry, Geometry geometry) {
    this.geometry = geometry;
    this.labelEntry = labelEntry;
    this.location = location;
  }

  @Override
  public Object getDefaultGeometry(){
    return geometry;
  }

  @Override
  public Object getAttribute(Name name) {
    if (name.getLocalPart().equals(labelEntry.getKey())) {
      return labelEntry.getValue();
    } else if (name.getLocalPart().equals(location.reference.attributeName())) {
      return location.attributeValue();
    } else {
      return null;
    }
  }

  public Object getLabel() {
    return labelEntry.getValue();
  }

  public Map.Entry<String, Object> getLabelEntry() {
    return labelEntry;
  }

  public String toString() {
    return "FE " + location.toString() + ": " + getLabel().toString();
  }
}
