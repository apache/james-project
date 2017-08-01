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

package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

public class FilterOperatorTest {

    @Test(expected=IllegalStateException.class)
    public void builderShouldThrowWhenOperatorIsNotGiven() {
        FilterOperator.builder().build();
    }

    @Test(expected=NullPointerException.class)
    public void builderShouldThrowWhenOperatorIsNull() {
        FilterOperator.builder().operator(null);
    }

    @Test(expected=IllegalStateException.class)
    public void builderShouldThrowWhenConditionsIsEmpty() {
        FilterOperator.builder().operator(Operator.AND).build();
    }

    @Test
    public void builderShouldWork() {
        ImmutableList<Filter> conditions = ImmutableList.of(FilterCondition.builder().build());
        FilterOperator expectedFilterOperator = new FilterOperator(Operator.AND, conditions);

        FilterOperator filterOperator = FilterOperator.builder()
            .operator(Operator.AND)
            .conditions(conditions)
            .build();

        assertThat(filterOperator).isEqualToComparingFieldByField(expectedFilterOperator);
    }

    @Test
    public void andFactoryMethodShouldThrowWhenNoArgument() {
        assertThatThrownBy(FilterOperator::and).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void andFactoryMethodShouldReturnRightOperator() {
        FilterCondition condition = FilterCondition.builder().inMailboxes("12").build();
        ImmutableList<Filter> conditions = ImmutableList.of(condition);
        FilterOperator expectedFilterOperator = new FilterOperator(Operator.AND, conditions);
        assertThat(FilterOperator.and(condition)).isEqualTo(expectedFilterOperator);
    }

    @Test
    public void orFactoryMethodShouldThrowWhenNoArgument() {
        assertThatThrownBy(FilterOperator::or).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void orFactoryMethodShouldReturnRightOperator() {
        FilterCondition condition = FilterCondition.builder().inMailboxes("12").build();
        ImmutableList<Filter> conditions = ImmutableList.of(condition);
        FilterOperator expectedFilterOperator = new FilterOperator(Operator.OR, conditions);
        assertThat(FilterOperator.or(condition)).isEqualTo(expectedFilterOperator);
    }

    @Test
    public void notFactoryMethodShouldThrowWhenNoArgument() {
        assertThatThrownBy(FilterOperator::not).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void notFactoryMethodShouldReturnRightOperator() {
        FilterCondition condition = FilterCondition.builder().inMailboxes("12").build();
        ImmutableList<Filter> conditions = ImmutableList.of(condition);
        FilterOperator expectedFilterOperator = new FilterOperator(Operator.NOT, conditions);
        assertThat(FilterOperator.not(condition)).isEqualTo(expectedFilterOperator);
    }

    @Test
    public void shouldRespectJavaBeanContract() {
        EqualsVerifier.forClass(FilterOperator.class).verify();
    }

    @Test
    public void toStringShouldBePretty() {
        FilterOperator testee = 
                FilterOperator.and(
                    FilterCondition.builder().inMailboxes("12","34").build(),
                    FilterOperator.or(
                        FilterOperator.not(
                            FilterCondition.builder().notInMailboxes("45").build()),
                        FilterCondition.builder().build()));
                
        String expected = Joiner.on('\n').join(
                            "FilterOperator{operator=AND}",
                            "  FilterCondition{inMailboxes=[12, 34]}",
                            "  FilterOperator{operator=OR}",
                            "    FilterOperator{operator=NOT}",
                            "      FilterCondition{notInMailboxes=[45]}",
                            "    FilterCondition{}",
                            "");
        String actual = testee.toString();
        assertThat(actual).isEqualTo(expected);
    }
}
