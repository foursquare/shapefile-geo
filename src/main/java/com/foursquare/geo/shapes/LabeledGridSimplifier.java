// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.foursquare.geo.shapes.indexing.CellLocation;
import com.foursquare.geo.shapes.indexing.CellLocationReference;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.TopologyException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simplifies a Shapefile by perfoming two actions:
 * 1. splits the features of the Shapefile into a grid according to
 * the reference levelSizes.
 * 2. if all features within a cell of the grid have the same label
 * (value of keyName attribute), all features of that cell are replaced by
 * a simple rectangle of the cell's bounds.
 */
public class LabeledGridSimplifier {
  static final Logger logger = LoggerFactory.getLogger(LabeledGridSimplifier.class);

  private LabeledGridSimplifier() {

  }

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

  // Equivalent to a "map"
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

    try {
      // then just split geometry by the grid intersections, creating a new feature for each
      for (int longIdx = childCellXMin; longIdx <= childCellXMax; ++longIdx) {
        for (int latIdx = childCellYMin; latIdx <= childCellYMax; ++latIdx) {
          CellLocation childLocation = featureEntry.location.child(longIdx, latIdx);
          Geometry cellEnvelope = childLocation.envelopeGeometry();
          if (cellEnvelope.intersects(featureEntry.geometry)) {
              subFeatures.add(new FeatureEntry(
                childLocation,
                featureEntry.getLabelEntry(),
                featureEntry.isWeakLabel(),
                cellEnvelope.intersection(featureEntry.geometry)
              ));

          }
        }
      }
    } catch (TopologyException te) {
      logger.warn("Failed to intersect geometry for entry: " + featureEntry);
      subFeatures.clear();
      subFeatures.add(featureEntry);
    } catch (IllegalArgumentException ie) {
      logger.warn("Failed to intersect GeometryCollection for entry: " + featureEntry);
      subFeatures.clear();
      subFeatures.add(featureEntry);
    }
    return subFeatures;
  }

  // Equivalent to a "reduce"
  private static SimplifiedFeatureEntries simplifySubFeatures(
    CellLocationReference reference,
    CellLocation location,
    List<FeatureEntry> coLocatedSubFeatures,
    boolean simplifySingleLabelCells,
    boolean finalRound
  ) {
    List<FeatureEntry> simplifiedSubFeatures = new ArrayList<FeatureEntry>();

    // Handle empty
    if (coLocatedSubFeatures.isEmpty()) {
      return new SimplifiedFeatureEntries(Collections.<FeatureEntry>emptyList());
    }

    // Handle all same (optimization)
    FeatureEntry first = coLocatedSubFeatures.get(0);
    boolean allSame = simplifySingleLabelCells;
    boolean allWeak = true;
    if (simplifySingleLabelCells) {
      Object singleLabel = first.getLabel();
      for (FeatureEntry entry : coLocatedSubFeatures) {
        if (!entry.getLabel().equals(singleLabel)) {
          allSame = false;
        }
        if (!entry.isWeakLabel()) {
          allWeak = false;
        }
      }
    }

    if (allSame && !allWeak) {
      simplifiedSubFeatures.add(first.sibling(
        first.getLabel(),
        false, // not a weak (water) label
        first.location.envelopeGeometry()
      ));
      return new SimplifiedFeatureEntries(simplifiedSubFeatures);
    } else if (!allWeak) {
      // No simplifications
      if (!finalRound) {
        return new SimplifiedFeatureEntries(Collections.<FeatureEntry>emptyList(), coLocatedSubFeatures);
      } else {
        List<ImmutablePair<Object, Geometry>> labeledGeoms = new ArrayList<ImmutablePair<Object, Geometry>>();
        for (FeatureEntry entry : coLocatedSubFeatures) {
          labeledGeoms.add(ImmutablePair.of(entry.getLabel(), entry.geometry));
        }
        List<ImmutablePair<Object, Geometry>> unionedGeoms =
          SimplifierUtils.unionByLabel(reference.getGeometryFactory(), labeledGeoms);
        for (ImmutablePair<Object, Geometry> labeledGeom: unionedGeoms) {
          simplifiedSubFeatures.add(first.sibling(
            labeledGeom.getLeft(),
            false,
            labeledGeom.getRight()
          ));
        }
        return new SimplifiedFeatureEntries(simplifiedSubFeatures);
      }
    } else {
      // all labels are weak, emit nothing for this cell
      return new SimplifiedFeatureEntries(Collections.<FeatureEntry>emptyList());
    }
  }

  private static List<FeatureEntry> iterativelySimplify(
    CellLocationReference reference,
    List<FeatureEntry> origFeatures,
    boolean simplifySingleLabelCells
  ) {
    List<FeatureEntry> finalSimplified = new ArrayList<FeatureEntry>();
    List<FeatureEntry> currentFeatures = origFeatures;

    int numLevels = reference.numLevels();
    for(int iteration = 0; iteration < numLevels; iteration++) {
      logger.info("iterativelySimplify: round {} of {}", iteration + 1, numLevels);
      boolean finalRound = iteration == reference.numLevels() - 1;
      Map<CellLocation, List<FeatureEntry>> subFeatures = new HashMap<CellLocation, List<FeatureEntry>>();
      // Map feature -> subFeature
      for (FeatureEntry feature : currentFeatures) {
        if (SimplifierUtils.isValidGeometry(feature.geometry)) {
          for (FeatureEntry subFeature : makeSubFeatures(feature)) {
            if (!subFeatures.containsKey(subFeature.location)) {
              subFeatures.put(subFeature.location, new ArrayList<FeatureEntry>());
            }
            subFeatures.get(subFeature.location).add(subFeature);
          }
        }
      }

      List<FeatureEntry> mustIterate = new ArrayList<FeatureEntry>();

      // Reduce subFeatures -> simpleFeatures
      for (Map.Entry<CellLocation, List<FeatureEntry>> colocatedSubFeatures : subFeatures.entrySet()) {
        SimplifiedFeatureEntries simplified = simplifySubFeatures(
          reference,
          colocatedSubFeatures.getKey(),
          colocatedSubFeatures.getValue(),
          simplifySingleLabelCells,
          finalRound
        );
        finalSimplified.addAll(simplified.finished);
        mustIterate.addAll(simplified.toSimplify);
      }
      currentFeatures = mustIterate;
    }

    finalSimplified.addAll(currentFeatures);
    return finalSimplified;
  }

  /**
   * Simplify the set of features.
   * @param reference the location reference (bounding box, etc)
   * @param features the input set of features
   * @param simplifySingleLabelCells when false, no simplification is performed,
   *                                 location index attributes are generated (for fast lookup when loaded),
   *                                 and shapes are split by the requested levelSizes,
   *                                 but when shapes within a cell all share the same label,
   *                                 the cell will not be replaced with a simple rectangle
   *                                 see {@link com.foursquare.geo.shapes.SimplifiedShapefileGeo#load} keepGeometry.
   * @return a set of simplified features
   */
  public static Iterable<FeatureEntry> simplify(
    CellLocationReference reference,
    List<FeatureEntry> features,
    boolean simplifySingleLabelCells
  ) {
    return iterativelySimplify(reference, features, simplifySingleLabelCells);
  }
}
