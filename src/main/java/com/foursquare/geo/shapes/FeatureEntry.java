// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.foursquare.geo.shapes.indexing.CellLocation;
import com.vividsolutions.jts.geom.Geometry;
import org.opengis.feature.type.Name;

import java.util.AbstractMap;
import java.util.Map;


public class FeatureEntry implements WritableFeature {
  private final Map.Entry<String, Object> labelEntry;
  final public CellLocation location;
  final public Geometry geometry;
  final private boolean isWeakLabel;

  public FeatureEntry(
    CellLocation location,
    Map.Entry<String, Object> labelEntry,
    boolean isWeakLabel,
    Geometry geometry
  ) {
    this.geometry = geometry;
    this.labelEntry = labelEntry;
    this.location = location;
    this.isWeakLabel = isWeakLabel;
  }

  public FeatureEntry sibling(
    Object label,
    boolean isWeakLabel,
    Geometry geometry
  ) {

    Map.Entry<String, Object> labelEntry = new AbstractMap.SimpleImmutableEntry<String, Object>(
      this.labelEntry.getKey(),
      label
    );

    return new FeatureEntry(
      location,
      labelEntry,
      isWeakLabel,
      geometry
    );
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

  /**
   * The label associated with this feature
   * @return the label
   */
  public Object getLabel() {
    return labelEntry.getValue();
  }

  /**
   * The label entry (label name and value) associated with this feature
   * @return the entry
   */
  public Map.Entry<String, Object> getLabelEntry() {
    return labelEntry;
  }

  @Override
  public String toString() {
    return "FE " + location.toString() + ": " + getLabel().toString() + " is weak: " + isWeakLabel;
  }

  /** A weak label (e.g. a labeled water) feature can be unioned with a non-weak label
   * in the same cell during simplification, but a cell consisting only of weak labels
   * should be considered to have no features and should be omitted.
   * @return the label type
   */
  public boolean isWeakLabel() {
    return isWeakLabel;
  }
}
