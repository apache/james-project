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

import org.apache.james.mailbox.MessageUid;
import org.junit.jupiter.api.Test;

class MessageRangeTest {

    @Test
    void givenSomeNumbersToRangeShouldReturnThreeRanges() {
        List<MessageRange> ranges = MessageRange.toRanges(
                Arrays.asList(
                        MessageUid.of(1L),
                        MessageUid.of(2L),
                        MessageUid.of(3L),
                        MessageUid.of(5L),
                        MessageUid.of(6L),
                        MessageUid.of(9L)));
        assertThat(ranges).containsExactly(
                MessageRange.range(MessageUid.of(1), MessageUid.of(3)), 
                MessageRange.range(MessageUid.of(5), MessageUid.of(6)), 
                MessageUid.of(9).toRange());
    }
    
    @Test
    void givenASingleNumberToRangeShouldReturnOneRange() {
        List<MessageRange> ranges = MessageRange.toRanges(Arrays.asList(MessageUid.of(1L)));
        assertThat(ranges).containsExactly(MessageUid.of(1).toRange());
    }
    
    // Test for MAILBOX-56
    @Test
    void testTwoSeqUidToRange() {
        List<MessageRange> ranges = MessageRange.toRanges(Arrays.asList(MessageUid.of(1L), MessageUid.of(2L)));
        assertThat(ranges).containsExactly(MessageRange.range(MessageUid.of(1), MessageUid.of(2)));
    }

    @Test
    void includeShouldBeTrueWhenAfterFrom() {
        MessageRange range = MessageRange.from(MessageUid.of(3));
        boolean actual = range.includes(MessageUid.of(5));
        assertThat(actual).isTrue();
    }

    @Test
    void includeShouldBeFalseWhenBeforeFrom() {
        MessageRange range = MessageRange.from(MessageUid.of(3));
        boolean actual = range.includes(MessageUid.of(1));
        assertThat(actual).isFalse();
    }

    @Test
    void includeShouldBeTrueWhenEqualsFrom() {
        MessageRange range = MessageRange.from(MessageUid.of(3));
        boolean actual = range.includes(MessageUid.of(3));
        assertThat(actual).isTrue();
    }

    @Test
    void includeShouldBeFalseWhenDifferentOne() {
        MessageRange range = MessageUid.of(3).toRange();
        boolean actual = range.includes(MessageUid.of(1));
        assertThat(actual).isFalse();
    }

    @Test
    void includeShouldBeTrueWhenEqualsOne() {
        MessageRange range = MessageUid.of(3).toRange();
        boolean actual = range.includes(MessageUid.of(3));
        assertThat(actual).isTrue();
    }

    @Test
    void includeShouldBeFalseWhenBeforeRange() {
        MessageRange range = MessageRange.range(MessageUid.of(3), MessageUid.of(6));
        boolean actual = range.includes(MessageUid.of(1));
        assertThat(actual).isFalse();
    }

    @Test
    void includeShouldBeTrueWhenEqualsFromRange() {
        MessageRange range = MessageRange.range(MessageUid.of(3), MessageUid.of(6));
        boolean actual = range.includes(MessageUid.of(3));
        assertThat(actual).isTrue();
    }

    @Test
    void includeShouldBeTrueWhenInRange() {
        MessageRange range = MessageRange.range(MessageUid.of(3), MessageUid.of(6));
        boolean actual = range.includes(MessageUid.of(4));
        assertThat(actual).isTrue();
    }

    @Test
    void includeShouldBeTrueWhenEqualsToRange() {
        MessageRange range = MessageRange.range(MessageUid.of(3), MessageUid.of(6));
        boolean actual = range.includes(MessageUid.of(6));
        assertThat(actual).isTrue();
    }

    @Test
    void includeShouldBeFalseWhenAfterRange() {
        MessageRange range = MessageRange.range(MessageUid.of(3), MessageUid.of(6));
        boolean actual = range.includes(MessageUid.of(7));
        assertThat(actual).isFalse();
    }
}
