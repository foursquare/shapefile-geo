// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.TopologyException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SimplifierUtils {
  static final Logger logger = LoggerFactory.getLogger(SimplifierUtils.class);
  static final int ImpossiblyLowPoints = 2;
  static final int SuspiciouslyLowPoints = 10;
  static final double AreaThreshold = 0.0000001;

  private SimplifierUtils() {

  }

  public static boolean isValidGeometry(Geometry geometry) {
    int points = geometry.getNumPoints();
    if (points <= ImpossiblyLowPoints || (points <= SuspiciouslyLowPoints && geometry.getArea() < AreaThreshold)) {
      logger.info("Dropping geometry {}", geometry);
      return false;
    } else {
      return true;
    }
  }

  public static List<ImmutablePair<Object, Geometry>> unionByLabel(
    GeometryFactory geometryFactory,
    List<ImmutablePair<Object, Geometry>> labeledFeatures
  ) {
    // Group the geometries by label, and add them to the list.
    HashMap<Object, List<Geometry>> singleLabelGeometries = new HashMap<Object, List<Geometry>>();
    for (ImmutablePair<Object, Geometry> feature: labeledFeatures) {
      Object label = feature.getLeft();
      Geometry geom = feature.getRight();
      if (isValidGeometry(geom)) {
        List<Geometry> geomList = singleLabelGeometries.get(label);
        if (geomList == null) {
          geomList = new ArrayList<Geometry>();
          singleLabelGeometries.put(label, geomList);
        }
        geomList.add(geom);
      }
    }

    List<ImmutablePair<Object, Geometry>> unionedGeoms = new ArrayList<ImmutablePair<Object, Geometry>>();
    // A list of coordinates can be converted to a lineString, which can create a geometry.
    for (Map.Entry<Object, List<Geometry>> entry: singleLabelGeometries.entrySet()) {
      Geometry unionedGeom = null;
      Object label = entry.getKey();
      List<Geometry> geoms = entry.getValue();
      int numGeoms = geoms.size();

      if (label == null) {
        logger.warn("Skipping null label group containing {} features", geoms.size());
        continue;
      }
      boolean unionError = false;
      try {
        if (numGeoms == 0) {
          throw new IllegalArgumentException("unexpected 0 geometries for label " + label);
        } else if (numGeoms == 1) {
          unionedGeom = geoms.get(0);
        } else if (numGeoms == 2) {
          unionedGeom = geoms.get(0).union(geoms.get(1));
        } else {

          unionedGeom = geometryFactory.buildGeometry(geoms).union();
          //parallelUnion(entry.getValue(), false);
        }

        unionedGeoms.add(ImmutablePair.of(label, unionedGeom));
      } catch (TopologyException te) {
        unionError = true;
      } catch (IllegalArgumentException ae) {
        unionError = true;
      }

      if (unionError) {
        logger.info("Could not union geometries for label: {} Adding separate geometries.", label);
        for (Geometry geom: geoms) {
          unionedGeoms.add(ImmutablePair.of(label, geom));
        }
      }
    }
    return unionedGeoms;
  }
}
