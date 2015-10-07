/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.elasticsearch.query;

import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.FilterBuilders.termFilter;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

import com.google.common.collect.Lists;
import org.apache.james.mailbox.model.SearchQuery;
import org.elasticsearch.index.query.QueryBuilder;
import org.junit.Test;

import java.util.List;
import java.util.stream.Stream;

public class FilteredQueryCollectorTest {

    @Test
    public void emptyStreamShouldBeCollectedAsEmptyFilteredQueryRepresentation() throws Exception {
        List<FilteredQueryRepresentation> emptyFilteredQueryRepresentationList = Lists.newArrayList();
        FilteredQueryRepresentation collectionResult = emptyFilteredQueryRepresentationList
            .stream()
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.AND));
        assertThat(collectionResult.getFilter()).isEmpty();
        assertThat(collectionResult.getQuery()).isEmpty();
    }

    @Test
    public void queryAloneShouldBeWellCollected() throws Exception {
        FilteredQueryRepresentation collectionResult = Stream.of(FilteredQueryRepresentation.fromQuery(matchAllQuery()))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.AND));
        assertThat(collectionResult.getFilter()).isEmpty();
        assertThat(collectionResult.getQuery()).isPresent();
        assertThatJson(collectionResult.getQuery().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"match_all\":{}}");
    }

    @Test
    public void filterAloneShouldBeWellCollected() throws Exception {
        FilteredQueryRepresentation collectionResult = Stream.of(FilteredQueryRepresentation.fromFilter(termFilter("field", "value")))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.AND));
        assertThat(collectionResult.getFilter()).isPresent();
        assertThat(collectionResult.getQuery()).isEmpty();
        assertThatJson(collectionResult.getFilter().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"term\":{\"field\":\"value\"}}");
    }

    @Test
    public void aggregationBetweenQueryAndFilterShouldWork() throws Exception {
        FilteredQueryRepresentation collectionResult = Stream.of(
            FilteredQueryRepresentation.fromFilter(termFilter("field", "value")),
            FilteredQueryRepresentation.fromQuery(matchAllQuery()))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.AND));
        assertThat(collectionResult.getFilter()).isPresent();
        assertThat(collectionResult.getQuery()).isPresent();
        assertThatJson(collectionResult.getQuery().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"match_all\":{}}");
        assertThatJson(collectionResult.getFilter().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"term\":{\"field\":\"value\"}}");
    }

    @Test
    public void queryAggregationShouldWork() throws Exception {
        FilteredQueryRepresentation collectionResult = Stream.of(
            FilteredQueryRepresentation.fromQuery(matchAllQuery()),
            FilteredQueryRepresentation.fromQuery(matchAllQuery()))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.AND));
        assertThat(collectionResult.getFilter()).isEmpty();
        assertThat(collectionResult.getQuery()).isPresent();
        assertThatJson(collectionResult.getQuery().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"bool\":{\"must\":[{\"match_all\":{}},{\"match_all\":{}}]}}");
    }

    @Test
    public void filterAggregationShouldWork() throws Exception {
        FilteredQueryRepresentation collectionResult = Stream.of(
            FilteredQueryRepresentation.fromFilter(termFilter("field", "value")),
            FilteredQueryRepresentation.fromFilter(termFilter("field", "value")))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.AND));
        assertThat(collectionResult.getFilter()).isPresent();
        assertThat(collectionResult.getQuery()).isEmpty();
        assertThatJson(collectionResult.getFilter().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"bool\":{\"must\":[{\"term\":{\"field\":\"value\"}},{\"term\":{\"field\":\"value\"}}]}}");
    }

    @Test
    public void emptyStreamShouldBeCollectedAsEmptyFilteredQueryRepresentationOnNor() throws Exception {
        List<FilteredQueryRepresentation> emptyFilteredQueryRepresentationList = Lists.newArrayList();
        FilteredQueryRepresentation collectionResult = emptyFilteredQueryRepresentationList
            .stream()
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.NOR));
        assertThat(collectionResult.getFilter()).isEmpty();
        assertThat(collectionResult.getQuery()).isEmpty();
    }

    @Test
    public void queryAloneShouldBeWellCollectedOnNor() throws Exception {
        FilteredQueryRepresentation collectionResult = Stream.of(FilteredQueryRepresentation.fromQuery(matchAllQuery()))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.NOR));
        assertThat(collectionResult.getFilter()).isEmpty();
        assertThat(collectionResult.getQuery()).isPresent();
        assertThatJson(collectionResult.getQuery().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"bool\":{\"must_not\":{\"match_all\":{}}}}");
    }

    @Test
    public void filterAloneShouldBeWellCollectedOnNor() throws Exception {
        FilteredQueryRepresentation collectionResult = Stream.of(FilteredQueryRepresentation.fromFilter(termFilter("field", "value")))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.NOR));
        assertThat(collectionResult.getFilter()).isPresent();
        assertThat(collectionResult.getQuery()).isEmpty();
        assertThatJson(collectionResult.getFilter().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"bool\":{\"must_not\":{\"term\":{\"field\":\"value\"}}}}");
    }

    @Test
    public void aggregationBetweenQueryAndFilterShouldWorkOnNor() throws Exception {
        FilteredQueryRepresentation collectionResult = Stream.of(
            FilteredQueryRepresentation.fromFilter(termFilter("field", "value")),
            FilteredQueryRepresentation.fromQuery(matchAllQuery()))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.NOR));
        assertThat(collectionResult.getFilter()).isPresent();
        assertThat(collectionResult.getQuery()).isPresent();
        assertThatJson(collectionResult.getQuery().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"bool\":{\"must_not\":{\"match_all\":{}}}}");
        assertThatJson(collectionResult.getFilter().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"bool\":{\"must_not\":{\"term\":{\"field\":\"value\"}}}}");
    }

    @Test
    public void queryAggregationShouldWorkOnNor() throws Exception {
        FilteredQueryRepresentation collectionResult = Stream.of(
            FilteredQueryRepresentation.fromQuery(matchAllQuery()),
            FilteredQueryRepresentation.fromQuery(matchAllQuery()))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.NOR));
        assertThat(collectionResult.getFilter()).isEmpty();
        assertThat(collectionResult.getQuery()).isPresent();
        assertThatJson(collectionResult.getQuery().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"bool\":{\"must\":{\"bool\":{\"must_not\":{\"match_all\":{}}}},\"must_not\":{\"match_all\":{}}}}");
    }

    @Test
    public void filterAggregationShouldWorkOnNor() throws Exception {
        FilteredQueryRepresentation collectionResult = Stream.of(
            FilteredQueryRepresentation.fromFilter(termFilter("field", "value")),
            FilteredQueryRepresentation.fromFilter(termFilter("field", "value")))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.NOR));
        assertThat(collectionResult.getFilter()).isPresent();
        assertThat(collectionResult.getQuery()).isEmpty();
        assertThatJson(collectionResult.getFilter().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo(
                "{\"bool\":{\"must\":{\"bool\":{\"must_not\":{\"term\":{\"field\":\"value\"}}}},\"must_not\":{\"term\":{\"field\":\"value\"}}}}");
    }

    @Test
    public void emptyStreamShouldBeCollectedAsEmptyFilteredQueryRepresentationOnOr() throws Exception {
        List<FilteredQueryRepresentation> emptyFilteredQueryRepresentationList = Lists.newArrayList();
        FilteredQueryRepresentation collectionResult = emptyFilteredQueryRepresentationList
            .stream()
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.OR));
        assertThat(collectionResult.getFilter()).isEmpty();
        assertThat(collectionResult.getQuery()).isEmpty();
    }

    @Test
    public void queryAloneShouldBeWellCollectedOnOr() throws Exception {
        FilteredQueryRepresentation collectionResult = Stream.of(FilteredQueryRepresentation.fromQuery(matchAllQuery()))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.OR));
        assertThat(collectionResult.getFilter()).isEmpty();
        assertThat(collectionResult.getQuery()).isPresent();
        assertThatJson(collectionResult.getQuery().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"match_all\":{}}");
    }

    @Test
    public void filterAloneShouldBeWellCollectedOnOr() throws Exception {
        FilteredQueryRepresentation collectionResult = Stream.of(FilteredQueryRepresentation.fromFilter(termFilter("field", "value")))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.OR));
        assertThat(collectionResult.getFilter()).isPresent();
        assertThat(collectionResult.getQuery()).isEmpty();
        assertThatJson(collectionResult.getFilter().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"term\":{\"field\":\"value\"}}");
    }

    @Test
    public void aggregationBetweenQueryAndFilterShouldWorkOnOr() throws Exception {
        FilteredQueryRepresentation collectionResult = Stream.of(
            FilteredQueryRepresentation.fromFilter(termFilter("field", "value")),
            FilteredQueryRepresentation.fromQuery(matchAllQuery()))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.OR));
        assertThat(collectionResult.getFilter()).isPresent();
        assertThat(collectionResult.getQuery()).isPresent();
        assertThatJson(collectionResult.getQuery().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"match_all\":{}}");
        assertThatJson(collectionResult.getFilter().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"term\":{\"field\":\"value\"}}");
    }

    @Test
    public void queryAggregationShouldWorkOnOr() throws Exception {
        FilteredQueryRepresentation collectionResult = Stream.of(
            FilteredQueryRepresentation.fromQuery(matchAllQuery()),
            FilteredQueryRepresentation.fromQuery(matchAllQuery()))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.OR));
        assertThat(collectionResult.getFilter()).isEmpty();
        assertThat(collectionResult.getQuery()).isPresent();
        assertThatJson(collectionResult.getQuery().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"bool\":{\"should\":[{\"match_all\":{}},{\"match_all\":{}}]}}");
    }

    @Test
    public void filterAggregationShouldWorkOnOr() throws Exception {
        FilteredQueryRepresentation collectionResult = Stream.of(
            FilteredQueryRepresentation.fromFilter(termFilter("field", "value")),
            FilteredQueryRepresentation.fromFilter(termFilter("field", "value")))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.OR));
        assertThat(collectionResult.getFilter()).isPresent();
        assertThat(collectionResult.getQuery()).isEmpty();
        assertThatJson(collectionResult.getFilter().get().toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .isEqualTo("{\"bool\":{\"should\":[{\"term\":{\"field\":\"value\"}},{\"term\":{\"field\":\"value\"}}]}}");
    }

}
