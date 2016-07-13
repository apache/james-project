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

import org.junit.Test;

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
    public void shouldRespectJavaBeanContract() {
        EqualsVerifier.forClass(FilterOperator.class).verify();
    }
}
