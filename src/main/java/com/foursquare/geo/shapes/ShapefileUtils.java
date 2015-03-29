// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.vividsolutions.jts.geom.GeometryFactory;
import org.geotools.data.*;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.AttributeTypeBuilder;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureTypeImpl;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.Name;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;


public final class ShapefileUtils {
  private ShapefileUtils() {

  }

  private static class WritableSimpleFeature implements WritableFeature {
    private SimpleFeature simpleFeature;
    public WritableSimpleFeature(SimpleFeature simpleFeature) {
      this.simpleFeature = simpleFeature;
    }
    public Object getDefaultGeometry() {
      return simpleFeature.getDefaultGeometry();
    }
    public Object getAttribute(Name name) {
      return simpleFeature.getAttribute(name);
    }
  }

  /**
   * Creates a WritableFeature from a SimpleFeature
   * @param simpleFeature the feature to convert
   * @return a WritableFeature representing the given SimpleFeature
   */
  public static final WritableFeature writableSimpleFeature(SimpleFeature simpleFeature) {
    return new WritableSimpleFeature(simpleFeature);
  }

  public static final GeometryFactory GEOMETRY_FACTORY = JTSFactoryFinder.getGeometryFactory(null);

  private static class FeatureIterator implements Iterator<SimpleFeature> {
    private ShapefileDataStore featureStore;
    private SimpleFeatureIterator featureSourceIterator;
    private boolean shouldDispose;
    private boolean canHaveNext;
    public FeatureIterator(ShapefileDataStore featureStore, boolean shouldDispose) throws IOException {
      this.featureStore = featureStore;
      this.featureSourceIterator = this.featureStore.getFeatureSource().getFeatures().features();
      this.shouldDispose = shouldDispose;
      this.canHaveNext = true;
    }

    @Override
    public boolean hasNext() {
      if (!canHaveNext) {
        return false;
      }

      if (!featureSourceIterator.hasNext()) {
        featureSourceIterator.close();
        if (shouldDispose) {
          featureStore.dispose();
        }
        canHaveNext = false;
        return false;
      } else {
        return true;
      }
    }

    @Override
    public SimpleFeature next() {
      return featureSourceIterator.next();
    }
  }

  private static class ClosingFeatureIterable implements Iterable<SimpleFeature> {
    private URL url;
    public ClosingFeatureIterable(URL url) {
      this.url = url;
    }

    @Override
    public Iterator<SimpleFeature> iterator() {
      try {
        return new FeatureIterator(featureStore(url), true);
      } catch (IOException ioe) {
        return Collections.<SimpleFeature>emptyList().iterator();
      }
    }
  }

  private static class FeatureIterable implements Iterable<SimpleFeature> {
    private ShapefileDataStore dataStore;
    public FeatureIterable(ShapefileDataStore dataStore) {
      this.dataStore = dataStore;
    }

    @Override
    public Iterator<SimpleFeature> iterator() {
      try {
        return new FeatureIterator(dataStore, false);
      } catch (IOException ioe) {
        return Collections.<SimpleFeature>emptyList().iterator();
      }
    }
  }

  /**
   * Opens a Shapefile as a FeatureStore
   * @param path location of .shp file
   * @return a ShapefileDataStore representing the file
   * @throws IOException if there is an issue opening the file
   */
  public static ShapefileDataStore featureStore(String path) throws IOException {
    URL url;
    try {
      url = new File(path).toURI().toURL();
    } catch (MalformedURLException urlEx) {
      throw new FileNotFoundException(urlEx.getMessage());
    }
    return featureStore(url);
  }

  /**
   * Opens a Shapefile as a FeatureStore.
   * @param url location of .shp file
   * @return a ShapefileDataStore representing the file
   * @throws IOException if there is an issue opening the file
   */
  public static ShapefileDataStore featureStore(URL url) throws IOException {
    HashMap<String, Serializable> dataStoreParams = new HashMap<String, Serializable>();
    dataStoreParams.put(ShapefileDataStoreFactory.URLP.key, url);
    dataStoreParams.put(ShapefileDataStoreFactory.MEMORY_MAPPED.key, Boolean.TRUE);
    dataStoreParams.put(ShapefileDataStoreFactory.CACHE_MEMORY_MAPS.key, Boolean.FALSE);
    dataStoreParams.put(ShapefileDataStoreFactory.CREATE_SPATIAL_INDEX.key, Boolean.FALSE);
    DataStoreFactorySpi dataStoreFactory = new ShapefileDataStoreFactory();
    ShapefileDataStore dataStore = (ShapefileDataStore) dataStoreFactory.createDataStore(dataStoreParams);
    dataStore.setStringCharset(Charset.forName("UTF-8"));
    return dataStore;
  }

  /**
   * Presents a Shapefile file as iterable collection of SimpleFeatures.
   * @param path location of .shp file
   * @return an iterable collection of SimpleFeatures
   * @throws IOException if there is an issue opening the file
   */
  public static Iterable<SimpleFeature> featureIterator(String path) throws IOException {
    URL url;
    try {
      url = new File(path).toURI().toURL();
    } catch (MalformedURLException urlEx) {
      throw new FileNotFoundException(urlEx.getMessage());
    }
    return new ClosingFeatureIterable(url);
  }

  /**
   * Presents a Shapefile file as iterable collection of SimpleFeatures.
   * @param url location of .shp file
   * @return an iterable collection of SimpleFeatures
   * @throws IOException if there is an issue opening the file
   */
  public static Iterable<SimpleFeature> featureIterator(URL url) throws IOException {
    return new ClosingFeatureIterable(url);
  }

  /**
   * Presents a Shapefile file as iterable collection of SimpleFeatures.
   * It is the responsibility of the caller to .dispose() of the store.
   * @param featureStore a Shapefile store
   * @return an iterable collection of SimpleFeatures
   */
  public static Iterable<SimpleFeature> featureIterator(ShapefileDataStore featureStore) {
    return new FeatureIterable(featureStore);
  }

  /**
   * Creates a new Feature Store for writing features
   * @param originalSource the original feature source, used for bounds and geometry descriptor information
   * @param path the location where the store will be saved
   * @param attributeTypes a map of attribute information (name, type) to create the schema
   * @return an empty store for writing features
   * @throws IOException if the file cannot be created
   */
  public static AbstractDataStore featureStore(
    FeatureSource originalSource,
    String path,
    Map<String,Class<?>> attributeTypes
  ) throws IOException {
    // Create store
    DataStoreFactorySpi storeFactory = new ShapefileDataStoreFactory();
    File file = new java.io.File(path);
    HashMap<String, Serializable> createFlags = new HashMap<String, Serializable>();
    createFlags.put("url", file.toURI().toURL());
    ShapefileDataStore saveStore = (ShapefileDataStore)storeFactory.createNewDataStore(createFlags);
    // Set flags and descriptors
    saveStore.setStringCharset(Charset.forName("UTF-8"));
    FeatureType oldSchema = originalSource.getSchema();
    final List<AttributeDescriptor> descriptorList = new java.util.ArrayList<AttributeDescriptor>();
    descriptorList.add(oldSchema.getGeometryDescriptor());
    // Set attributes
    for (Map.Entry<String, Class<?>> entry: attributeTypes.entrySet()) {
      AttributeTypeBuilder keyTB = new AttributeTypeBuilder();
      keyTB.setName(entry.getKey());
      keyTB.setBinding(entry.getValue());
      keyTB.setNillable(true);
      AttributeDescriptor desc = keyTB.buildDescriptor(entry.getKey());
      descriptorList.add(desc);
    }
    // Finalize schema
    SimpleFeatureTypeImpl newSchema = new SimpleFeatureTypeImpl(
      new NameImpl(file.getName()),
      descriptorList,
      oldSchema.getGeometryDescriptor(),
      oldSchema.isAbstract(),
      oldSchema.getRestrictions(),
      oldSchema.getSuper(),
      null);
    saveStore.createSchema(newSchema);
    return saveStore;
  }

  /**
   * Adds features to the store.
   * @param featureStore the store.
   * @param features the features to write
   * @throws IOException if there is an issue writing the features
   * @see com.foursquare.geo.shapes.ShapefileUtils#featureStore
   */
  public static void addFeatures(AbstractDataStore featureStore, Iterable<? extends WritableFeature> features) throws IOException {
    Transaction transaction = Transaction.AUTO_COMMIT;
    SimpleFeatureType schema = featureStore.getSchema(featureStore.getNames().get(0));
    FeatureWriter<SimpleFeatureType, SimpleFeature> writer = featureStore.getFeatureWriterAppend(
      schema.getTypeName(),
      transaction
    );
    List<Name> attributeNames = new ArrayList<Name>();
    for (AttributeDescriptor desc : schema.getAttributeDescriptors()) {
      if (!(desc.getType() instanceof GeometryType)) {
        attributeNames.add(desc.getName());
      }
    }

    for (WritableFeature feature : features) {
      SimpleFeature newFeature = writer.next();
      newFeature.setDefaultGeometry(feature.getDefaultGeometry());
      for (Name name : attributeNames) {
        newFeature.setAttribute(name, feature.getAttribute(name));
      }
      writer.write();
    }
    writer.close();
    transaction.commit();
    transaction.close();
  }
}
