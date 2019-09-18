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
package org.apache.james.jmap.draft.model.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.TreeSet;

import org.junit.Test;

public class SortOrderTest {

    @Test
    public void sortOrderShouldNotBeNegative() {
        assertThatThrownBy(() -> SortOrder.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void sortOrderShouldSupportZero() {
        assertThat(SortOrder.of(0).getSortOrder()).isEqualTo(0);
    }

    @Test
    public void sortOrderShouldSupportPositiveInteger() {
        assertThat(SortOrder.of(4).getSortOrder()).isEqualTo(4);
    }

    @Test
    public void sortOrderShouldBeComparable() {
        TreeSet<SortOrder> sortedSet = new TreeSet<>();
        SortOrder sixtySix = SortOrder.of(66);
        SortOrder four = SortOrder.of(4);
        SortOrder five = SortOrder.of(5);
        sortedSet.add(sixtySix);
        sortedSet.add(four);
        sortedSet.add(five);
        assertThat(sortedSet).containsExactly(four, five, sixtySix);
    }
}