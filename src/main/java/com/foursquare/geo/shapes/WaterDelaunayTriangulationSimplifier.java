// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.foursquare.geo.shapes.indexing.CellLocationReference;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.triangulate.DelaunayTriangulationBuilder;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WaterDelaunayTriangulationSimplifier {
  static final Logger logger = LoggerFactory.getLogger(WaterDelaunayTriangulationSimplifier.class);

  static final int ImpossiblyLowPoints = 2;
  static final int SuspiciouslyLowPoints = 10;
  static final double AreaThreshold = 0.0000001;


  static class LabeledWaterFeature {
    public final Geometry geometry;
    public final Object label;
    public final boolean wasMultiLabel;
    public LabeledWaterFeature(Geometry geometry, Object label, boolean wasMultiLabel) {
      this.geometry = geometry;
      this.label = label;
      this.wasMultiLabel = wasMultiLabel;
    }
  }

  private WaterDelaunayTriangulationSimplifier() {

  }

  private static List<Geometry> triangularize(GeometryFactory geometryFactory, Geometry geometry) {
    DelaunayTriangulationBuilder dtb = new DelaunayTriangulationBuilder();
    dtb.setSites(geometry);
    Geometry triangularizedGeom = dtb.getTriangles(geometryFactory);
    List<Geometry> triangles = new ArrayList<Geometry>(triangularizedGeom.getNumGeometries());
    for(int i = 0; i < triangularizedGeom.getNumGeometries(); ++i) {
      triangles.add(triangularizedGeom.getGeometryN(i));
    }
    return triangles;
  }

  private static Set<Object> labelSet(List<FeatureEntry> featureEntries) {
    if (featureEntries == null || featureEntries.isEmpty()) {
      return Collections.emptySet();
    }
    HashSet<Object> labels = new HashSet<Object>();
    for (FeatureEntry featureEntry: featureEntries) {
      labels.add(featureEntry.getLabel());
    }
    return labels;
  }

  private static <E> E containsAny(Set<E> set, Collection<E> other) {
    if (set.isEmpty() || other.isEmpty()) {
      return null;
    }
    for (E elem: other) {
      if (set.contains(elem)) {
        return elem;
      }
    }
    return null;
  }

  private static <E> E head(Collection<E> c) {
    Iterator<E> it = c.iterator();
    if (it.hasNext()) {
      return it.next();
    }
    return null;
  }


  private static List<LabeledWaterFeature> labelTriangles(
    GeometryFactory geometryFactory,
    Map<Coordinate, List<FeatureEntry>> coordToFeat,
    List<Geometry> triangles
  ) {
    List<LabeledWaterFeature> waterFeatures = new ArrayList<LabeledWaterFeature>();
    int totalTriangles = triangles.size();
    logger.debug("Total triangles {}", totalTriangles);
    for(Geometry triangle: triangles) {
      Coordinate[] coords = triangle.getCoordinates();
      if (coords.length != 4) {
        throw new IllegalArgumentException(
          "Expected 4 coords per triangle, but " + triangle + " has " + coords.length
        );
      }
      // Generate label sets for each coordinate
      List<Set<Object>> coordLabels = new ArrayList<Set<Object>>();
      for (Coordinate coord: coords) {
        coordLabels.add(labelSet(coordToFeat.get(coord)));
      }

      Set<Object> commonLabels = new HashSet<Object>();
      commonLabels.addAll(coordLabels.get(0));
      for (Set<Object> coordLabel: coordLabels) {
        commonLabels.retainAll(coordLabel);
      }


      Object commonLabel = head(commonLabels);
      if (commonLabel != null) {
        /* If the intersection of sets is non-empty, the triangle
         * can be classified with a single label. Any one will do. */
        waterFeatures.add(new LabeledWaterFeature(triangle, commonLabel, false));
      } else {
        /* Not all points can agree on a single label.  In this case we can create
           one of two shapes: a trapezoid with a triangle hat:
                /|
               /1|
              /__|
             / 2 |
            /____|

           or an inscribed triangle:
                /|
               /1|
              /__|
             /\ /|
            /2\/3|

           or another shape, but these seemed okay.
         */

        // first, check if triangle overlaps with any land. if so, skip.
        boolean overlaps = false;
        Set<Geometry> touchingGeometries = new HashSet<Geometry>();
        for(Coordinate coord: coords) {
          if (overlaps) {
            break;
          }
          List<FeatureEntry> feats = coordToFeat.get(coord);
          if (feats == null) {
            continue;
          }
          for (FeatureEntry feat: feats) {
            if (touchingGeometries.add(feat.geometry)) {
              if (feat.geometry.overlaps(triangle)) {
                overlaps = true;
                break;
              }
            }
          }
        }
        if (overlaps) {
          continue;
        }

        // Compare the coordinates to each other. For each pair, is there a common label they agree on?
        List<ImmutablePair<Object, Coordinate>> labeledCoords = new ArrayList<ImmutablePair<Object, Coordinate>>();
        for (int i = 0; i < coords.length; ++i) {
          for (int j = i + 1; j < coords.length; ++j) {
            Coordinate iCoord = coords[i];
            Coordinate jCoord = coords[j];
            Object aCommonLabel = containsAny(coordLabels.get(i), coordLabels.get(j));
            if (aCommonLabel != null) {
              // If two coords share a common label, add those coords to that label
              labeledCoords.add(ImmutablePair.of(aCommonLabel, iCoord));
              labeledCoords.add(ImmutablePair.of(aCommonLabel, jCoord));
            } else {
              // TODO(johng) pick most popular, instead of head.
              Object iLabel = head(coordLabels.get(i));
              Object jLabel = head(coordLabels.get(j));
              if (iLabel != null && jLabel != null) {
                Coordinate coordMid = new Coordinate(
                  (iCoord.x + jCoord.x) / 2,
                  (iCoord.y + jCoord.y) / 2
                );
                labeledCoords.add(ImmutablePair.of(iLabel, iCoord));
                labeledCoords.add(ImmutablePair.of(iLabel, coordMid));
                labeledCoords.add(ImmutablePair.of(jLabel, jCoord));
                labeledCoords.add(ImmutablePair.of(jLabel, coordMid));
              }
            }
          }
        }

        // Group the coordinates by label, and add them to the list.
        HashMap<Object, List<Coordinate>> singleLabelCoords = new HashMap<Object, List<Coordinate>>();
        for (ImmutablePair<Object, Coordinate> labeledCoord: labeledCoords) {
          List<Coordinate> coordList = singleLabelCoords.get(labeledCoord.getLeft());
          if (coordList == null) {
            coordList = new ArrayList<Coordinate>();
            singleLabelCoords.put(labeledCoord.getLeft(), coordList);
          }
          coordList.add(labeledCoord.getRight());
        }

        // A list of coordinates can be converted to a lineString, which can create a geometry.
        for (Map.Entry<Object, List<Coordinate>> entry: singleLabelCoords.entrySet()) {
          int numCoords = entry.getValue().size();
          if (numCoords >= 3) {
            // convexHull is used because the trapezoid can potentially become a bowtie.
            Geometry labelGeom = geometryFactory.createLineString(
              entry.getValue().toArray(new Coordinate[numCoords])
            ).convexHull();
            waterFeatures.add(new LabeledWaterFeature(
              labelGeom,
              entry.getKey(),
              true
            ));
          }
        }
      }
    }
    return waterFeatures;
  }

  private static List<FeatureEntry> unionTrianglesByLabel(
    GeometryFactory geometryFactory,
    FeatureEntryFactory featureEntryFactory,
    List<LabeledWaterFeature> labeledTriangles
  ) {
    // Convert the labeled triangles to label -> geom pairs
    List<ImmutablePair<Object, Geometry>> labeledGeoms = new ArrayList<ImmutablePair<Object, Geometry>>();
    for (LabeledWaterFeature entry : labeledTriangles) {
      labeledGeoms.add(ImmutablePair.of(entry.label, entry.geometry));
    }

    // Group/Union by label
    List<ImmutablePair<Object, Geometry>> unionedGeoms =
      SimplifierUtils.unionByLabel(geometryFactory, labeledGeoms);

    // Convert to feature entries
    List<FeatureEntry> combinedTriangles = new ArrayList<FeatureEntry>();
    for (ImmutablePair<Object, Geometry> labeledGeom: unionedGeoms) {
      combinedTriangles.add(featureEntryFactory.featureEntry(
        labeledGeom.getLeft(),
        true, // Label is weak
        labeledGeom.getRight()
      ));
    }
    return combinedTriangles;
  }

  /**
   * Simplify the set of features.
   * @param reference the location reference (bounding box, etc)
   * @param featureEntryFactory  a factory instance for creating more features
   * @param features the input set of features
   * @return a set of simplified features
   */
  public static List<FeatureEntry> simplify(
    CellLocationReference reference,
    FeatureEntryFactory featureEntryFactory,
    Iterable<FeatureEntry> features
  ) {
    GeometryFactory geometryFactory = reference.getGeometryFactory();
    HashMap<Coordinate, List<FeatureEntry>> coordToFeat = new HashMap<Coordinate, List<FeatureEntry>>();
    ArrayList<Geometry> allLand = new ArrayList<Geometry>();
    for (FeatureEntry feature: features) {
      if (feature.getLabel() == null) {
        continue;
      }

      allLand.add(feature.geometry);
      for(Coordinate coord: feature.geometry.getCoordinates()) {
        List<FeatureEntry> entries = coordToFeat.get(coord);
        if (entries == null) {
          entries = new ArrayList<FeatureEntry>();
          coordToFeat.put(coord, entries);
        }
        entries.add(feature);
      }
    }
    long start = System.currentTimeMillis();
    logger.info("Perfoming union of all land");
    Geometry land = geometryFactory.buildGeometry(allLand).union();

    logger.info("Building water from land negative");
    Geometry water = geometryFactory.toGeometry(reference.getEnvelope()).difference(land);
    land = null; // GC-able
    List<Geometry> validSubGeometries = new ArrayList<Geometry>(water.getNumGeometries());

    logger.info("Filtering non-valid water features");
    for (int i = 0; i < water.getNumGeometries(); ++i) {
      Geometry geometry = water.getGeometryN(i);
      int points = geometry.getNumPoints();
      if (points <= ImpossiblyLowPoints || (points <= SuspiciouslyLowPoints && geometry.getArea() < AreaThreshold)) {
        logger.info("Dropping geometry {}", geometry);
      } else {
        validSubGeometries.add(geometry);
      }
    }
    water = null; // GC-able

    logger.info("Triangularizing water");
    List<Geometry> waterTriangles = new ArrayList<Geometry>();
    for (Geometry validWaterGeometry: validSubGeometries) {
      waterTriangles.addAll(triangularize(geometryFactory, validWaterGeometry));
    }
    validSubGeometries = null; // GC-able
    logger.info("Labeling triangles");
    List<LabeledWaterFeature> labeledTriangles = labelTriangles(geometryFactory, coordToFeat, waterTriangles);
    waterTriangles = null; // GC-able
    logger.info("Unioning same-labeled triangles");
    List<FeatureEntry> unionedLabeledWater = unionTrianglesByLabel(
      geometryFactory,
      featureEntryFactory,
      labeledTriangles
    );
    return unionedLabeledWater;
  }
}
