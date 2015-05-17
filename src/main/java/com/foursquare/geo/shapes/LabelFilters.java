// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import org.geotools.geometry.jts.ReferencedEnvelope;

import java.util.List;
import java.util.Set;

/**
 * A collection of LabelFilters
 */
public class LabelFilters {
  // Utility class
  private LabelFilters() {

  }

  /**
   * Replaces any returned non-valid label with a default.
   */
  public static class ValidLabelFilter implements LabelFilter {
    private final Set<Object> validLabels;
    private final Object defaultLabel;


    /**
     * Construct the filter. Set the defaultLabel to null and stack LabelApproximations
     * @param validLabels the set of values to consider valid (passed through)
     * @param defaultLabel the label to replace non-valid values.
     */
    public ValidLabelFilter(Set<Object> validLabels, Object defaultLabel) {
      this.validLabels = validLabels;
      this.defaultLabel = defaultLabel;

    }

    @Override
    public Object filterLabelForCoordinate(Coordinate coordinate, IndexedValues next) {
      Object label = next.labelForCoordinate(coordinate);
      if (validLabels.contains(label)) {
        return label;
      } else {
        return defaultLabel;
      }
    }
  }

  /**
   * Replaces any null label with a meridian-based timezone (Etc/GMT0 or Etc/GMT+/-H)
   * Note: the Etc-prefixed timezones are named opposite intuition. See
   * http://twiki.org/cgi-bin/xtra/tzdate?tz=Etc/GMT+5
   */
  public static class TimezoneLabelFilter implements LabelFilter {
    @Override
    public Object filterLabelForCoordinate(Coordinate coordinate, IndexedValues next) {
      Object label = next.labelForCoordinate(coordinate);
      if (label != null) {
        return label;
      }
      int closestMeridian = (int) -Math.round(coordinate.x / 15.0);
      String timezone;
      if (closestMeridian > 0) {
        timezone = "Etc/GMT+" + closestMeridian; // Etc/GMT+1
      } else {
        timezone = "Etc/GMT" + closestMeridian; // Etc/GMT0, Etc/GMT-10
      }
      return timezone;
    }
  }

  /**
   * Replaces any null label with a default value
   */
  public static class DefaultLabelFilter implements LabelFilter {
    private final Object defaultLabel;

    /**
     * Construct a filter with a given default.
     *
     * @param defaultLabel the default value to replace null.
     */
    public DefaultLabelFilter(Object defaultLabel) {
      this.defaultLabel = defaultLabel;
    }

    @Override
    public Object filterLabelForCoordinate(Coordinate coordinate, IndexedValues next) {
      Object label = next.labelForCoordinate(coordinate);
      if (label != null) {
        return label;
      } else {
        return defaultLabel;
      }
    }
  }

  /**
   * Returns null for labels outside the given envelope
   */
  public static class BoundingBoxFilter implements LabelFilter {
    private final ReferencedEnvelope envelope;

    /**
     * Constructs a filter using an envelope as the bounds
     * @param envelope the envelope of valid coordinate values
     */
    public BoundingBoxFilter(ReferencedEnvelope envelope) {
      this.envelope = envelope;
    }

    @Override
    public Object filterLabelForCoordinate(Coordinate coordinate, IndexedValues next) {
      if (envelope.covers(coordinate)) {
        return next.labelForCoordinate(coordinate);
      } else {
        return null;
      }
    }
  }

  /**
   * If the returned label is null, gets the point's colocated
   * features, if any, and returns the label of the closest feature
   */
  public static abstract class FeatureFilter implements LabelFilter {
    private GeometryFactory geometryFactory;
    public FeatureFilter(GeometryFactory geometryFactory) {
      this.geometryFactory = geometryFactory;
    }
    @Override
    public Object filterLabelForCoordinate(Coordinate coordinate, IndexedValues next) {
      Object label = next.labelForCoordinate(coordinate);
      if (label != null) {
        return label;
      }

      List<FeatureEntry> featureEntries = next.colocatedFeatures(coordinate);
      if (featureEntries.isEmpty()) {
        return null;
      }

      return bestLabel(geometryFactory.createPoint(coordinate), featureEntries);
    }

    abstract Object bestLabel(Point point, List<FeatureEntry> featureEntries);
  }


  /**
   * If the returned label is null, gets the point's colocated
   * features, if any, and returns the label of the closest feature
   */
  public static class FeatureDistanceFilter extends FeatureFilter {
    public FeatureDistanceFilter(GeometryFactory geometryFactory) {
      super(geometryFactory);
    }

    @Override
    protected Object bestLabel(Point point, List<FeatureEntry> featureEntries) {
      double best = Double.MAX_VALUE;
      Object bestLabel = null;

      for (FeatureEntry featureEntry : featureEntries) {
        double dist = featureEntry.geometry.distance(point);
        if (dist < best) {
          best = dist;
          bestLabel = featureEntry.getLabel();
        }
      }
      return bestLabel;
    }
  }

  /**
   * If the returned label is null, gets the point's colocated
   * features, if any, and returns the label of the feature whose
   * centroid is closest to the point
   */
  public static class FeatureCentroidDistanceFilter extends FeatureFilter {
    public FeatureCentroidDistanceFilter(GeometryFactory geometryFactory) {
      super(geometryFactory);
    }

    @Override
    protected Object bestLabel(Point point, List<FeatureEntry> featureEntries) {
      double best = Double.MAX_VALUE;
      Object bestLabel = null;

      for (FeatureEntry featureEntry : featureEntries) {
        double dist = featureEntry.geometry.getCentroid().distance(point);
        if (dist < best) {
          best = dist;
          bestLabel = featureEntry.getLabel();
        }
      }
      return bestLabel;
    }
  }

  /**
   * If the returned label is null, gets the point's colocated
   * features, if any, and returns the label of the feature whose
   * centroid is closest to the point
   */
  class FeatureBoundingBoxFilter extends FeatureFilter {
    public FeatureBoundingBoxFilter(GeometryFactory geometryFactory) {
      super(geometryFactory);
    }

    @Override
    protected Object bestLabel(Point point, List<FeatureEntry> featureEntries) {
      double best = Double.MAX_VALUE;
      Object bestLabel = null;

      for (FeatureEntry featureEntry : featureEntries) {
        Geometry envelope = featureEntry.geometry.getEnvelope();
        if (envelope.covers(point)) {
          double area = envelope.getArea();
          if (area < best) {
            best = area;
            bestLabel = featureEntry.getLabel();
          }
        }

      }
      return bestLabel;
    }
  }
}
