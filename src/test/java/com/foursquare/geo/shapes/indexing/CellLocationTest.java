// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes.indexing;


import com.vividsolutions.jts.geom.Coordinate;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.junit.Assert;
import org.junit.Test;
import org.opengis.referencing.crs.CoordinateReferenceSystem;


public class CellLocationTest {
  static final CoordinateReferenceSystem CRS = DefaultGeographicCRS.WGS84;
  /* Exact delta can be used because of the default precision model */
  static final double HighPrecisionDelta = 0.00000001;

  static final double DefaultEnvMinX = 0, DefaultEnvMaxX = 10;
  static final double DefaultEnvMinY = 0, DefaultEnvMaxY = 20;
  static final ReferencedEnvelope DefaultEnv = new ReferencedEnvelope(
    DefaultEnvMinX,
    DefaultEnvMaxX,
    DefaultEnvMinY,
    DefaultEnvMaxY,
    CRS
  );
  static final int DefaultCellSize = 2;
  static final CellLocationReference DefaultReference = new CellLocationReference(
    DefaultEnv,
    new int[] { DefaultCellSize, DefaultCellSize }
  );


  @Test
  public void testCellLocationSiblings() {
    CellLocation root = new CellLocation(DefaultReference);
    CellLocation bottomLeft = root.child(0, 0);
    CellLocation topLeft = root.child(0, 1);
    CellLocation topRight = root.child(1, 1);
    CellLocation bottomRight = root.child(1, 0);
    Assert.assertEquals(
      "cell size check X",
      bottomLeft.envelope().getMaxX(),
      DefaultEnvMaxX / DefaultCellSize,
      HighPrecisionDelta
    );
    Assert.assertEquals(
      "cell size check Y",
      bottomLeft.envelope().getMaxY(),
      DefaultEnvMaxY / DefaultCellSize,
      HighPrecisionDelta
    );
    Assert.assertEquals(
      "bottom: left.maxX == right.minX",
      bottomLeft.envelope().getMaxX(),
      bottomRight.envelope().getMinX(),
      HighPrecisionDelta
    );
    Assert.assertEquals(
      "top: left.maxX == right.minX",
      topLeft.envelope().getMaxX(),
      topRight.envelope().getMinX(),
      HighPrecisionDelta
    );
    Assert.assertEquals(
      "left: bottom.maxY == top.minX",
      bottomLeft.envelope().getMaxY(),
      topLeft.envelope().getMinY(),
      HighPrecisionDelta
    );
    Assert.assertEquals(
      "right: left.maxY == right.minY",
      bottomRight.envelope().getMaxY(),
      topRight.envelope().getMinY(),
      HighPrecisionDelta
    );
  }

  @Test
  public void testCellLocationHashcodeEquals() {
    CellLocationReference reference2 = new CellLocationReference(
      new ReferencedEnvelope(
        1, // Use differentMinX for this reference
        DefaultEnvMaxX,
        DefaultEnvMinY,
        DefaultEnvMaxY,
        CRS
      ),
      new int[]{DefaultCellSize}
    );

    CellLocation root1 = new CellLocation(DefaultReference);
    CellLocation root2 = new CellLocation(DefaultReference);
    CellLocation root3 = new CellLocation(reference2);
    CellLocation bottomLeft1 = new CellLocation(root1, 0, 0);
    CellLocation bottomLeft2 = new CellLocation(root2, 0, 0);
    CellLocation bottomLeft3 = new CellLocation(root3, 0, 0);

    Assert.assertEquals(
      "Hash codes of identical roots must match",
      root1.hashCode(),
      root2.hashCode()
    );

    Assert.assertEquals(
      "Hash codes of identical locations must match",
      bottomLeft1.hashCode(),
      bottomLeft2.hashCode()
    );

    Assert.assertTrue(
      "Identical location objects must be equal",
      root1.equals(root1)
    );

    Assert.assertTrue(
      "Identical locations must be equal",
      root1.equals(root2)
    );

    Assert.assertFalse(
      "Different locations must not be equal",
      root1.equals(bottomLeft1)
    );

    Assert.assertFalse(
      "Non-locations must not be equal",
      root1.equals(new Object())
    );

    Assert.assertTrue(
      "Identical locations must be equal",
      bottomLeft1.equals(bottomLeft2)
    );

    Assert.assertFalse(
      "Identical location paths with different references must not be equal",
      bottomLeft1.equals(bottomLeft3)
    );
  }

  @Test
  public void testCellLocationToString() {
    Assert.assertEquals(
      "Empty location reference toString is empty",
      "[]",
      new CellLocation(DefaultReference).toString()
    );
    Assert.assertEquals(
      "Empty location reference toString is empty",
      "[1,0 1,1]",
      new CellLocation(DefaultReference).child(1, 0).child(1, 1).toString()
    );
  }

  @Test
  public void testCellLocationAttributeValue() {
    // Test the attribute value serialization format
    Assert.assertEquals(
      "Empty location reference toString is empty",
      "",
      new CellLocation(DefaultReference).attributeValue()
    );
    CellLocation aGrandchild = new CellLocation(DefaultReference).child(1, 0).child(1, 1);

    Assert.assertEquals(
      "Empty location reference toString is empty",
      "1,0;1,1;",
      aGrandchild.attributeValue()
    );

    Assert.assertEquals(
      "Location attribute value can be deserialized",
      aGrandchild,
      CellLocation.fromAttributeValue(DefaultReference, aGrandchild.attributeValue())
    );
  }

  @Test
  public void testCellLocationParent() {
    CellLocation root = new CellLocation(DefaultReference);
    CellLocation child = root.child(0, 1);
    CellLocation grandchild = child.child(1, 0);
    Assert.assertEquals(
      "Grandchild's parent is child",
      child,
      grandchild.parent()
    );

    Assert.assertEquals(
      "Child's parent is root",
      root,
      child.parent()
    );

    Assert.assertNull(
      "Root's parent is null",
      root.parent()
    );

    try {
      CellLocation unexpected = grandchild.child(0, 0);
      Assert.fail("Expected an exception when depth would exceed levelSizes spec, but got: " + unexpected);
    } catch (IllegalArgumentException e) {

    }
  }

  @Test
  public void testCellLocationFromCoordinate() {
    // Create a reference with only 1 cell level
    CellLocationReference simpleReference = new CellLocationReference(
      DefaultEnv,
      new int[] {DefaultCellSize}
    );
    CellLocation root = new CellLocation(simpleReference);
    CellLocation bottomLeft = root.child(0, 0);
    CellLocation topLeft = root.child(0, 1);
    CellLocation topRight = root.child(1, 1);
    CellLocation bottomRight = root.child(1, 0);
    Assert.assertEquals(
      "coord minX,minY is in bottomLeft",
      bottomLeft,
      CellLocation.fromCoordinate(simpleReference, new Coordinate(DefaultEnvMinX, DefaultEnvMinY))
    );

    Assert.assertEquals(
      "coord 0.5,0.5 is in bottomLeft",
      bottomLeft,
      CellLocation.fromCoordinate(simpleReference, new Coordinate(0.5, 0.5))
    );

    Assert.assertEquals(
      "coord midPoint,0 is in bottomRight",
      bottomRight,
      CellLocation.fromCoordinate(simpleReference, new Coordinate(DefaultEnvMaxX / 2, 0))
    );

    Assert.assertEquals(
      "coord minPoint,midPoint is in topRight",
      topRight,
      CellLocation.fromCoordinate(simpleReference, new Coordinate(DefaultEnvMaxX / 2, DefaultEnvMaxY / 2))
    );

    Assert.assertEquals(
      "coord 0,midPoint is in topLeft",
      topLeft,
      CellLocation.fromCoordinate(simpleReference, new Coordinate(0, DefaultEnvMaxY / 2))
    );

    Assert.assertEquals(
      "coord ovr,ovr is in topRight",
      topRight,
      CellLocation.fromCoordinate(simpleReference, new Coordinate(DefaultEnvMaxX * 2, DefaultEnvMaxY * 2))
    );
  }
}
