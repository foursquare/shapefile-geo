// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.geo.shapes;

import com.vividsolutions.jts.geom.Coordinate;

import java.util.List;


/**
 * Base implementation of IndexedValues that provides filter chaining
 */
public abstract class BaseIndexedValues implements IndexedValues {
  /**
   * An stacking adapter for filters
   */
  static class FilteredIndexedValues extends BaseIndexedValues {
    private final LabelFilter labelFilter;
    private final IndexedValues next;

    public FilteredIndexedValues(LabelFilter labelFilter, IndexedValues indexedValues) {
      this.labelFilter = labelFilter;
      this.next = indexedValues;
    }

    @Override
    public Object labelForCoordinate(Coordinate coordinate) {
      return labelFilter.filterLabelForCoordinate(coordinate, next);
    }

    @Override
    public List<FeatureEntry> colocatedFeatures(Coordinate coordinate) {
      return next.colocatedFeatures(coordinate);
    }
  }

  @Override
  public IndexedValues with(LabelFilter filter) {
    return new FilteredIndexedValues(filter, this);
  }
}
