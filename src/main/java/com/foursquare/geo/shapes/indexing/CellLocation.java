// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes.indexing;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import org.geotools.geometry.jts.ReferencedEnvelope;

import java.util.Arrays;

/**
 * Defines a location in the index, relative to a {@link CellLocationReference}
 * The index represents the path to a node in a Quadtree-like structure,
 * but is suitable for representing a quadtree-like structure in a Map,
 * using CellLocation as a key.
 */
public class CellLocation {
  private final int[] locationIndices;
  public final CellLocationReference reference;

  /**
   * Creates a root index entry
   * @param reference the location reference for this root
   */
  public CellLocation(CellLocationReference reference) {
    this.reference = reference;
    locationIndices = new int[0];
  }

  /**
   * Creates a child index entry.
   * @param parent the parent of this entry
   * @param longIdx  the x (longitudinal)-index of the entry
   * @param latIdx  y (latitudinal)-index of the entry
   * @see CellLocation#child
   */
  public CellLocation(CellLocation parent, int longIdx, int latIdx) {
    if (parent.level() >= parent.reference.getLevelSizes().length) {
      throw new IllegalArgumentException("Parent CellLocation " +
        parent.toString() + " is already at max level depth"
      );
    }
    this.reference = parent.reference;
    locationIndices = Arrays.copyOf(
      parent.locationIndices,
      parent.locationIndices.length + 2);
    locationIndices[locationIndices.length - 2] = longIdx;
    locationIndices[locationIndices.length - 1] = latIdx;
  }

  private CellLocation(CellLocationReference reference, int[] locationIndices) {
    this.reference = reference;
    this.locationIndices = locationIndices;
  }

  /**
   * Deserializes a location based on an serialized attribute value, e.g. from a Shapefile
   * @param reference the location reference
   * @param attributeValue the serialized attribute value
   * @return a cell location representing the deserialized value
   */
  public static CellLocation fromAttributeValue(CellLocationReference reference, Object attributeValue) {
    String[] groups = attributeValue.toString().split(";");
    if (groups.length < 1 || groups.length > reference.numLevels()) {
      throw new RuntimeException(attributeValue + " has unexpected size");
    }

    int[] locationIndices = new int[groups.length * 2];

    for (int idx = 0; idx < groups.length; ++idx) {
      String[] indices = groups[idx].split(",");
      if (indices.length != 2) {
        throw new RuntimeException("group " + idx + " of " + attributeValue + " has unexpected size");
      }

      int longIdx = Integer.parseInt(indices[0]);
      int latIdx = Integer.parseInt(indices[1]);
      if (longIdx < 0 || longIdx >= reference.getLevelSize(idx) || latIdx < 0 || latIdx >= reference.getLevelSize(idx)) {
        throw new RuntimeException("group " + idx + " of " + attributeValue + " has unexpected value");
      }
      locationIndices[idx * 2] = longIdx;
      locationIndices[idx * 2 + 1] = latIdx;
    }
    return new CellLocation(reference, locationIndices);
  }

  /**
   * Returns the most precise index containing the coordinate within the parameters
   * of the reference
   * @param reference the location reference
   * @param coordinate a coordinate contained within the envelope of the reference
   * @return the most precise index containing the coordinate given the reference
   */
  public static CellLocation fromCoordinate(CellLocationReference reference, Coordinate coordinate) {
    Envelope worldEnvelope = reference.getEnvelope();
    double minX = worldEnvelope.getMinX();
    double minY = worldEnvelope.getMinY();
    double width = worldEnvelope.getWidth();
    double height = worldEnvelope.getHeight();
    int[] levelSizes = reference.getLevelSizes();
    int[] locationIndices = new int[2 * levelSizes.length];
    int m = 1;
    for (int idx = 0; idx < levelSizes.length; ++idx) {
      m *= levelSizes[idx];
      double cellWidth = width / m;
      double cellHeight = height / m;
      int indexX = (int) ((coordinate.x - minX) / cellWidth);
      int indexXFixed = Math.max(0, Math.min(indexX, levelSizes[idx] - 1));
      int indexY = (int) ((coordinate.y - minY) / cellHeight);
      int indexYFixed = Math.max(0, Math.min(indexY, levelSizes[idx] - 1));
      locationIndices[2 * idx] = indexXFixed;
      locationIndices[2 * idx + 1] = indexYFixed;
      minX += cellWidth * indexXFixed;
      minY += cellHeight * indexYFixed;
    }
    return new CellLocation(reference, locationIndices);
  }

  @Override
  public int hashCode() {
    return reference.hashCode() + Arrays.hashCode(locationIndices);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (!(o instanceof CellLocation)) {
      return false;
    } else {
      CellLocation otherLocation = (CellLocation) o;
      return reference.equals(otherLocation.reference) && Arrays.equals(locationIndices, otherLocation.locationIndices);
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(3 * locationIndices.length);
    sb.append("[");
    for (int idx = 0; idx < locationIndices.length; ++idx) {
      sb.append(locationIndices[idx]);
      if (idx % 2 == 0) {
        sb.append(',');
      } else if (idx != locationIndices.length - 1) {
        sb.append(' ');
      }
    }
    sb.append(']');
    return sb.toString();
  }

  /**
   * The depth level of this location. (The number of parents + 1)
   * @return the depth level of this location
   */
  public int level() {
    return locationIndices.length / 2;
  }

  /**
   * An envelope representing the bounds of the current index
   * @return the Envelope
   */
  public ReferencedEnvelope envelope() {
    Envelope worldEnvelope = reference.getEnvelope();
    double minX = worldEnvelope.getMinX();
    double minY = worldEnvelope.getMinY();
    double width = worldEnvelope.getWidth();
    double height = worldEnvelope.getHeight();
    int[] levelSizes = reference.getLevelSizes();
    int m = 1;
    for (int idx = 0; idx < locationIndices.length / 2; ++idx) {
      m *= levelSizes[idx];
      minX += (width / m) * locationIndices[idx * 2];
      minY += (height / m) * locationIndices[idx * 2 + 1];
    }
    double maxX = minX + (width / m);
    double maxY = minY + (height / m);
    return new ReferencedEnvelope(
      minX,
      maxX,
      minY,
      maxY,
      reference.getEnvelope().getCoordinateReferenceSystem()
    );
  }

  /**
   * The {@link com.foursquare.geo.shapes.indexing.CellLocation#envelope}, as a geometry
   * @return the envelope geometry
   * @see com.foursquare.geo.shapes.indexing.CellLocation#envelope
   */
  public Geometry envelopeGeometry() {
    return reference.getGeometryFactory().toGeometry(envelope());
  }

  /**
   * A serialized value representing the index location.
   * The reference is not part of the serialization.
   * @return a string-representation of the location
   */
  public Object attributeValue() {
    StringBuilder sb = new StringBuilder(3 * locationIndices.length);
    for (int idx = 0; idx < locationIndices.length; ++idx) {
      sb.append(locationIndices[idx]);
      if (idx % 2 == 0) {
        sb.append(',');
      } else {
        sb.append(';');
      }
    }
    return sb.toString();
  }

  /**
   * The parent
   * @return parent location, or null if this is the root location
   */
  public CellLocation parent() {
    if (locationIndices.length < 2) {
      return null;
    }
    return new CellLocation(reference, Arrays.copyOf(locationIndices, locationIndices.length - 2));
  }

  /**
   * A child
   * @param longIdx  the x (longitudinal)-index of the entry
   * @param latIdx  y (latitudinal)-index of the entry
   * @return a child location for the given indices
   */
  public CellLocation child(int longIdx, int latIdx) {
    return new CellLocation(this, longIdx, latIdx);
  }
}
