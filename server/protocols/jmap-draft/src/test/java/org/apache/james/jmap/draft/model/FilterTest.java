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
package org.apache.james.jmap.draft.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.IntStream;

import org.apache.james.jmap.json.ObjectMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class FilterTest {

    private ObjectMapper parser;

    @Before
    public void setup() {
        parser = new ObjectMapperFactory(new InMemoryId.Factory(), new InMemoryMessageId.Factory()).forParsing();
    }

    @Test
    public void emptyFilterConditionShouldBeDeserialized() throws Exception {
        String json = "{}";
        Filter expected = FilterCondition.builder()
                .build();
        Filter actual = parser.readValue(json, Filter.class);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void singleFilterConditionShouldBeDeserialized() throws Exception {
        String json = "{\"inMailboxes\": [\"12\",\"34\"]}";
        Filter expected = FilterCondition.builder().inMailboxes("12","34").build();
        Filter actual = parser.readValue(json, Filter.class);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void doubleFilterConditionShouldBeDeserialized() throws Exception {
        String json = "{\"inMailboxes\": [\"12\",\"34\"], \"notInMailboxes\": [\"45\",\"67\"]}";
        Filter expected = FilterCondition.builder()
                .inMailboxes("12","34")
                .notInMailboxes("45","67")
                .build();
        Filter actual = parser.readValue(json, Filter.class);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void operatorWithSingleConditionShouldBeDeserialized() throws Exception {
        String json = "{\"operator\": \"AND\", \"conditions\": [{\"inMailboxes\": [\"12\",\"34\"]}]}";
        Filter expected = FilterOperator.and(FilterCondition.builder().inMailboxes("12","34").build());
        Filter actual = parser.readValue(json, Filter.class);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void complexFilterShouldBeDeserialized() throws Exception {
        String json = "{\"operator\": \"AND\", \"conditions\": ["
                + "         {\"inMailboxes\": [\"12\",\"34\"]},"
                + "         {\"operator\": \"OR\", \"conditions\": ["
                + "                 {\"operator\": \"NOT\", \"conditions\": ["
                + "                         {\"notInMailboxes\": [\"45\"]}]},"
                + "                 {}]}]}";
        Filter expected = 
                FilterOperator.and(
                        FilterCondition.builder().inMailboxes("12","34").build(),
                        FilterOperator.or(
                                FilterOperator.not(
                                        FilterCondition.builder().notInMailboxes("45").build()),
                                FilterCondition.builder().build()));
        Filter actual = parser.readValue(json, Filter.class);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void flattenShouldNoopWhenCondition() {
        FilterCondition condition = FilterCondition.builder()
            .to("bob@domain.tld")
            .build();

        assertThat(condition.breadthFirstVisit(10))
            .containsExactly(condition);
    }

    @Test
    public void breadthFirstVisitShouldUnboxOneLevelOperator() {
        FilterCondition condition1 = FilterCondition.builder()
            .to("bob@domain.tld")
            .build();
        FilterCondition condition2 = FilterCondition.builder()
            .to("alice@domain.tld")
            .build();

        assertThat(FilterOperator.and(condition1, condition2)
                .breadthFirstVisit())
            .containsExactly(condition1, condition2);
    }

    @Test
    public void breadthFirstVisitShouldUnboxTwoLevelOperator() {
        FilterCondition condition1 = FilterCondition.builder()
            .to("bob@domain.tld")
            .build();
        FilterCondition condition2 = FilterCondition.builder()
            .to("alice@domain.tld")
            .build();
        FilterCondition condition3 = FilterCondition.builder()
            .to("cedric@domain.tld")
            .build();

        assertThat(FilterOperator.and(condition1, FilterOperator.and(condition2, condition3))
                .breadthFirstVisit())
            .containsOnly(condition1, condition2, condition3);
    }

    @Test
    public void breadthFirstVisitShouldBeBreadthFirst() {
        FilterCondition condition1 = FilterCondition.builder()
            .to("bob@domain.tld")
            .build();
        FilterCondition condition2 = FilterCondition.builder()
            .to("alice@domain.tld")
            .build();
        FilterCondition condition3 = FilterCondition.builder()
            .to("cedric@domain.tld")
            .build();
        FilterCondition condition4 = FilterCondition.builder()
            .to("david@domain.tld")
            .build();

        assertThat(FilterOperator.and(condition1, FilterOperator.and(condition2, condition3), condition4)
                .breadthFirstVisit())
            .containsOnly(condition1, condition2, condition3, condition4);
    }

    @Test
    public void breadthFirstVisitShouldAllowUpToLimitNesting() {
        FilterCondition condition = FilterCondition.builder()
            .to("bob@domain.tld")
            .build();

        Filter nestedFilter = IntStream.range(0, 10).boxed().reduce(
            (Filter) condition,
            (filter, i) -> FilterOperator.and(filter),
            (f1, f2) -> {
                throw new RuntimeException("unsupported combination");
            });

        assertThat(nestedFilter.breadthFirstVisit())
            .containsExactly(condition);
    }

    @Test
    public void breadthFirstVisitShouldRejectDeepNesting() {
        FilterCondition condition = FilterCondition.builder()
            .to("bob@domain.tld")
            .build();

        Filter nestedFilter = IntStream.range(0, 11).boxed().reduce(
            (Filter) condition,
            (filter, i) -> FilterOperator.and(filter),
            (f1, f2) -> {
                throw new RuntimeException("unsupported combination");
            });

        assertThatThrownBy(nestedFilter::breadthFirstVisit)
            .isInstanceOf(Filter.TooDeepFilterHierarchyException.class);
    }
}
