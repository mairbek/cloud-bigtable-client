/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.bigtable.hbase.adapters.filters;

import com.google.bigtable.v2.RowFilter;
import com.google.bigtable.v2.RowFilter.Chain;
import com.google.bigtable.v2.RowFilter.Interleave;
import com.google.cloud.bigtable.hbase.adapters.filters.FilterAdapterContext.ContextCloseable;
import com.google.common.base.Optional;

import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.FilterList.Operator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapts a FilterList into either a RowFilter with chaining or interleaving.
 *
 * @author sduskis
 * @version $Id: $Id
 */
public class FilterListAdapter
    extends TypedFilterAdapterBase<FilterList> implements UnsupportedStatusCollector<FilterList> {
  private final FilterAdapter subFilterAdapter;

  /**
   * <p>Constructor for FilterListAdapter.</p>
   *
   * @param subFilterAdapter a {@link com.google.cloud.bigtable.hbase.adapters.filters.FilterAdapter} object.
   */
  public FilterListAdapter(FilterAdapter subFilterAdapter) {
    this.subFilterAdapter = subFilterAdapter;
  }

  /** {@inheritDoc} */
  @Override
  public RowFilter adapt(FilterAdapterContext context, FilterList filter) throws IOException {
    try (ContextCloseable ignored = context.beginFilterList(filter)) {
      List<RowFilter> childFilters = collectChildFilters(context, filter);
      if (childFilters.isEmpty()) {
        return null;
      } else if (childFilters.size() == 1) {
        return childFilters.get(0);
      } else  if (filter.getOperator() == Operator.MUST_PASS_ALL) {
        return RowFilter.newBuilder()
            .setChain(Chain.newBuilder().addAllFilters(childFilters))
            .build();
      } else {
        return RowFilter.newBuilder()
            .setInterleave(Interleave.newBuilder().addAllFilters(childFilters))
            .build();
      }
    }
  }

  List<RowFilter> collectChildFilters(FilterAdapterContext context, FilterList filter)
      throws IOException {
    List<RowFilter> result = new ArrayList<>();
    for (Filter subFilter : filter.getFilters()) {
      Optional<RowFilter> potentialFilter =
          subFilterAdapter.adaptFilter(context, subFilter);
      if (potentialFilter.isPresent()) {
        result.add(potentialFilter.get());
      }
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public FilterSupportStatus isFilterSupported(
      FilterAdapterContext context,
      FilterList filter) {
    List<FilterSupportStatus> unsupportedSubfilters = new ArrayList<>();
    try (ContextCloseable ignored = context.beginFilterList(filter)) {
      collectUnsupportedStatuses(context, filter, unsupportedSubfilters);
    }
    if (unsupportedSubfilters.isEmpty()) {
      return FilterSupportStatus.SUPPORTED;
    } else {
      return FilterSupportStatus.newCompositeNotSupported(unsupportedSubfilters);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void collectUnsupportedStatuses(
      FilterAdapterContext context,
      FilterList filter,
      List<FilterSupportStatus> unsupportedStatuses) {
    for (Filter subFilter : filter.getFilters()) {
      subFilterAdapter.collectUnsupportedStatuses(context, subFilter, unsupportedStatuses);
    }
  }
}
