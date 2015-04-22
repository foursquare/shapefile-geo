// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes.indexing;

import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.PrecisionModel;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * A reference for {@link com.foursquare.geo.shapes.indexing.CellLocation}s, containing
 * information on the bounding {@link com.vividsolutions.jts.geom.Envelope} and
 * the number of levels and their sizes.
 */
public class CellLocationReference {
  static final Logger logger = LoggerFactory.getLogger(CellLocationReference.class);
  private final ReferencedEnvelope envelope;
  private final int[] levelSizes;
  private final int hashCodeValue;
  private final GeometryFactory geometryFactory;
  /**
   * The prefix used for identifying indexing attributes in a schema
   */
  public static final String AttributePrefix = "GI";


  /**
   * Creates a new reference
   * @param envelope  the bounding envelope, which allows points
   *                  to be associated with their containing {@link com.foursquare.geo.shapes.indexing.CellLocation}s
   * @param levelSizes a list representing the maximum depth of the index (length of array)
   *                   and the 2D branching factor at each level (the value at each index)
   *                   a quadtree-like index, with a maximum depth of 5 could be realized
   *                   as <code>new int[]{2, 2, 2, 2, 2}</code>
   */
  public CellLocationReference(ReferencedEnvelope envelope, int[] levelSizes) {
    this.envelope = envelope;
    this.levelSizes = levelSizes.clone();
    this.hashCodeValue = envelope.hashCode() + Arrays.hashCode(this.levelSizes);
    geometryFactory = new GeometryFactory(
      new PrecisionModel(PrecisionModel.FLOATING_SINGLE)
    );
    logger.debug("GeometryFactory using PrecisionModel {}", geometryFactory.getPrecisionModel());
  }


  /**
   * Deserializes an attribute name into a CellLocationReference
   * @param envelope the bounding envelope (e.g. from a Shapefile bounds)
   * @param attributeName the name (e.g. from a Shapefile schema)
   * @return a CellLocationReference represented by the name
   * @see CellLocationReference#attributeName
   */
  public static CellLocationReference fromAttributeName(ReferencedEnvelope envelope, String attributeName) {
    if (!attributeName.startsWith(AttributePrefix)) {
      throw new RuntimeException(attributeName + " does not start with expected prefix " + AttributePrefix);
    }

    String[] sizes = attributeName.substring(AttributePrefix.length()).split("_");
    if (sizes.length < 1) {
      throw new RuntimeException(attributeName + " has no sizes specified");
    }

    int[] levelSizes = new int[sizes.length];
    for (int idx = 0; idx < sizes.length; ++idx) {
      levelSizes[idx] = Integer.parseInt(sizes[idx]);
    }
    return new CellLocationReference(envelope, levelSizes);
  }

  /**
   * The GeometryFactory used when converting index locations to {@link com.vividsolutions.jts.geom.Geometry}s
   * @return the geometry factory
   */
  public GeometryFactory getGeometryFactory() {
    return geometryFactory;
  }

  /**
   * Level sizes internal representation for {@link com.foursquare.geo.shapes.indexing.CellLocation}
   * package-level access to protect internal state without copying
   * @return
   */
  int[] getLevelSizes() {
    return levelSizes;
  }

  /**
   * The maximum depth of the location index, equivalent to the maximum
   * depth of a quadtree-like structure.
   * @return
   */
  public int numLevels() {
    return levelSizes.length;
  }

  /**
   * The size (2D branching factor) for the given level.
   * @param level the level
   * @return the branching factor. A value of 2 would mean 4 children.
   */
  public int getLevelSize(int level) {
    return levelSizes[level];
  }

  /**
   * The bounding envelope for this reference
   * @return the envelope
   */
  public ReferencedEnvelope getEnvelope() {
    return envelope;
  }

  @Override
  public int hashCode() {
    return hashCodeValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (!(o instanceof CellLocationReference)) {
      return false;
    } else {
      CellLocationReference otherRef = (CellLocationReference) o;
      return this.envelope.equals(otherRef.envelope) && Arrays.equals(levelSizes, otherRef.levelSizes);
    }
  }

  /**
   * A serialization of the attribute name for use in a Shapefile
   * @return the name
   */
  public String attributeName() {
    StringBuilder sb = new StringBuilder(3 * levelSizes.length);
    sb.append(AttributePrefix);
    for (int idx = 0; idx < levelSizes.length; ++idx) {
      if (idx != 0) {
        sb.append('_');
      }
      sb.append(levelSizes[idx]);
    }
    return sb.toString();
  }

  /**
   * The class type to use to represent the {@link com.foursquare.geo.shapes.indexing.CellLocation#attributeValue}
   * in a schema.  Used for building a new Shapefile schema that includes the index as an attribute.
   * @return the class
   */
  public Class<?> attributeType() {
    return String.class;
  }
}
