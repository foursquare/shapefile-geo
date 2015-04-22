// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.foursquare.geo.shapes.indexing.CellLocation;
import com.foursquare.geo.shapes.indexing.CellLocationReference;
import com.vividsolutions.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
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

  public Map.Entry<String, Object> labelEntry(Object label) {
    return new AbstractMap.SimpleImmutableEntry<String, Object>(
      labelAttribute,
      label
    );
  }

  public FeatureEntry featureEntry(SimpleFeature feature) {
    Geometry geom = (Geometry) feature.getDefaultGeometry();
    CellLocation location = maybeLocationFromFeature(feature);
    Map.Entry<String, Object> labelEntry = new AbstractMap.SimpleImmutableEntry<String, Object>(
      labelAttribute,
      feature.getAttribute(labelAttribute)
    );
    return new FeatureEntry(location, labelEntry, false, geom);
  }

  public FeatureEntry featureEntry(Object label, Geometry geometry) {
    return new FeatureEntry(initialLocation, labelEntry(label), false, geometry);
  }

  public FeatureEntry featureEntry(Object label, boolean isWeakLabel, Geometry geometry) {
    return new FeatureEntry(initialLocation, labelEntry(label), isWeakLabel, geometry);
  }

  private CellLocation maybeLocationFromFeature(SimpleFeature feature) {
    Object attrValueOpt = feature.getAttribute(reference.attributeName());
    if (attrValueOpt == null) {
      return initialLocation;
    } else {
      return CellLocation.fromAttributeValue(reference, attrValueOpt);
    }
  }

  public List<FeatureEntry> featureEntries(Iterable<SimpleFeature> features) {
    List<FeatureEntry> featureEntries = new ArrayList<FeatureEntry>();
    for(SimpleFeature feature: features) {
      FeatureEntry featureEntry = featureEntry((feature));
      featureEntries.add(featureEntry);
    }
    return featureEntries;
  }
}
