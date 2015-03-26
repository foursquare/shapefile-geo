// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.foursquare.geo.shapes.indexing.CellLocation;
import com.foursquare.geo.shapes.indexing.CellLocationReference;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LabeledGridSimplifier {

  private static class SimplifiedFeatureEntries {
    public final List<FeatureEntry> finished;
    public final List<FeatureEntry> toSimplify;
    public SimplifiedFeatureEntries(List<FeatureEntry> finished) {
      this.finished = finished;
      this.toSimplify = Collections.emptyList();
    }
    public SimplifiedFeatureEntries(List<FeatureEntry> finished, List<FeatureEntry> toSimplify) {
      this.finished = finished;
      this.toSimplify = toSimplify;
    }
  }

  private static List<FeatureEntry> makeSubFeatures(FeatureEntry featureEntry) {
    List<FeatureEntry> subFeatures = new ArrayList<FeatureEntry>();
    Envelope envelope = featureEntry.location.envelope();
    int numChildCells = featureEntry.location.reference.getLevelSize(
      featureEntry.location.level());
    double childWidth = envelope.getWidth() / numChildCells;
    double childHeight = envelope.getHeight() / numChildCells;
    double startX = envelope.getMinX();
    double startY = envelope.getMinY();

    // Find the over-approximation of the child cell coverage by taking the envelope of the geometry
    Envelope geomEnvelope = featureEntry.geometry.getEnvelopeInternal();
    int childCellXMin = (int) ((geomEnvelope.getMinX() - startX) / childWidth);
    int childCellXMax = Math.min(numChildCells - 1, (int) ((geomEnvelope.getMaxX() - startX) / childWidth));
    int childCellYMin = (int) ((geomEnvelope.getMinY() - startY) / childHeight);
    int childCellYMax = Math.min(numChildCells - 1, (int) ((geomEnvelope.getMaxY() - startY) / childHeight));

    // then just split geometry by the grid intersections, creating a new feature for each
    for (int longIdx = childCellXMin; longIdx <= childCellXMax; ++longIdx) {
      for (int latIdx = childCellYMin; latIdx <= childCellYMax; ++latIdx) {
        CellLocation childLocation = featureEntry.location.child(longIdx, latIdx);
        Geometry cellEnvelope = childLocation.envelopeGeometry();
        if (cellEnvelope.intersects(featureEntry.geometry)) {
          subFeatures.add(new FeatureEntry(
            childLocation,
            featureEntry.getLabelEntry(),
            cellEnvelope.intersection(featureEntry.geometry)));
        }
      }
    }
    return subFeatures;
  }

  private static SimplifiedFeatureEntries simplifySubFeatures(
    CellLocation location,
    List<FeatureEntry> coLocatedSubFeatures
  ) {
    List<FeatureEntry> simplifiedSubFeatures = new ArrayList<FeatureEntry>();

    // Handle empty
    if (coLocatedSubFeatures.isEmpty()) {
      return new SimplifiedFeatureEntries(Collections.<FeatureEntry>emptyList());
    }

    // Handle all same (optimization)
    FeatureEntry first = coLocatedSubFeatures.get(0);
    boolean allSame = true;
    Object singleLabel = first.getLabel();
    for (FeatureEntry entry: coLocatedSubFeatures) {
      if (!entry.getLabel().equals(singleLabel)) {
        allSame = false;
        break;
      }
    }

    if (allSame) {
      simplifiedSubFeatures.add(new FeatureEntry(
        first.location,
        first.getLabelEntry(),
        first.location.envelopeGeometry()));
      return new SimplifiedFeatureEntries(simplifiedSubFeatures);
    } else {
      return new SimplifiedFeatureEntries(Collections.<FeatureEntry>emptyList(), coLocatedSubFeatures);
    }
  }

  private static List<FeatureEntry> iterativelySimplify(CellLocationReference reference, List<FeatureEntry> origFeatures) {
    List<FeatureEntry> finalSimplified = new ArrayList<FeatureEntry>();
    List<FeatureEntry> currentFeatures = origFeatures;


    for(int iteration = 0; iteration < reference.numLevels(); ++iteration) {
      Map<CellLocation, List<FeatureEntry>> subFeatures = new HashMap<CellLocation, List<FeatureEntry>>();
      // Map feature -> subFeature
      for (FeatureEntry feature : currentFeatures) {
        for (FeatureEntry subFeature : makeSubFeatures(feature)) {
          if (!subFeatures.containsKey(subFeature.location)) {
            subFeatures.put(subFeature.location, new ArrayList<FeatureEntry>());
          }
          subFeatures.get(subFeature.location).add(subFeature);
        }
      }

      List<FeatureEntry> mustIterate = new ArrayList<FeatureEntry>();

      // Reduce subFeatures -> simpleFeatures
      for (Map.Entry<CellLocation, List<FeatureEntry>> colocatedSubFeatures : subFeatures.entrySet()) {
        SimplifiedFeatureEntries simplified = simplifySubFeatures(colocatedSubFeatures.getKey(), colocatedSubFeatures.getValue());
        finalSimplified.addAll(simplified.finished);
        mustIterate.addAll(simplified.toSimplify);
      }
      currentFeatures = mustIterate;
    }

    finalSimplified.addAll(currentFeatures);
    return finalSimplified;
  }

  public static Iterable<FeatureEntry> simplify(
    CellLocationReference reference,
    Iterable<SimpleFeature> features,
    String keyName
  ) {
    FeatureEntryFactory featureEntryFactory = new FeatureEntryFactory(reference, keyName);
    List<FeatureEntry> featureEntries = new ArrayList<FeatureEntry>();
    // TODO(johnG) parallelize
    for(SimpleFeature feature: features) {
      FeatureEntry featureEntry = featureEntryFactory.featureEntry((feature));
      featureEntries.add(featureEntry);
    }
    return iterativelySimplify(reference, featureEntries);
  }
}
