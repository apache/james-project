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
package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.apache.james.mailbox.model.MessageRange;
import org.junit.Test;

public class MessageRangeTest {

    @Test
    public void givenSomeNumbersToRangeShouldReturnThreeRanges() {
        List<MessageRange> ranges = MessageRange.toRanges(Arrays.asList(1L,2L,3L,5L,6L,9L));
        assertThat(ranges).containsExactly(
                MessageRange.range(1, 3), 
                MessageRange.range(5, 6), 
                MessageRange.one(9));
    }
    
    @Test
    public void givenASingleNumberToRangeShouldReturnOneRange() {
        List<MessageRange> ranges = MessageRange.toRanges(Arrays.asList(1L));
        assertThat(ranges).containsExactly(MessageRange.one(1));
    }
    
    // Test for MAILBOX-56
    @Test
    public void testTwoSeqUidToRange() {
        List<MessageRange> ranges = MessageRange.toRanges(Arrays.asList(1L,2L));
        assertThat(ranges).containsExactly(MessageRange.range(1, 2));
    }
    
    @Test
    public void splitASingletonRangeShouldReturnASingleRange() {
        MessageRange one = MessageRange.one(1);
        List<MessageRange> ranges = one.split(2);
        assertThat(ranges).containsExactly(MessageRange.one(1));
    }

    @Test
    public void splitUnboundedRangeShouldReturnTheSameRange() {
        MessageRange from = MessageRange.from(1);
        List<MessageRange> ranges = from.split(2);
        assertThat(ranges).containsExactly(MessageRange.from(1));
    }
    
    @Test
    public void splitTenElementsRangeShouldReturn4Ranges() {
        MessageRange range = MessageRange.range(1,10);
        List<MessageRange> ranges = range.split(3);
        assertThat(ranges).containsExactly(
                MessageRange.range(1, 3), 
                MessageRange.range(4, 6), 
                MessageRange.range(7, 9), 
                MessageRange.one(10));
    }
}
