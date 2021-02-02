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
package org.apache.james.imap.api.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MessageRange;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

class UidRangeTest {
    private static final MessageUid _1 = MessageUid.of(1);
    private static final MessageUid _2 = MessageUid.of(2);
    private static final MessageUid _3 = MessageUid.of(3);
    private static final MessageUid _4 = MessageUid.of(4);
    private static final MessageUid _5 = MessageUid.of(5);
    private static final MessageUid _10 = MessageUid.of(10);

    @Test
    void singleArgConstructorShouldBuildSingletonRange() {
        assertThat(new UidRange(_1)).containsOnly(_1);
    }

    @Test
    void lowBoundArgShouldBeGreaterThanHighBound() {
        assertThatThrownBy(() -> new UidRange(_2, _1))
            .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    void identicalLowBoundAndHighBoundShouldBuildASingleton() {
        assertThat(new UidRange(_2, _2)).hasSize(1);
    }

    
    @Test
    void regularRangeShouldBuild() {
        assertThat(new UidRange(_1, _2)).hasSize(2);
    }

    @Test
    void includesShouldReturnFalseWhenSmallerValue() {
        assertThat(new UidRange(_2, _3).includes(_1)).isFalse();
    }

    @Test
    void includesShouldReturnFalseWhenGreaterValue() {
        assertThat(new UidRange(_2, _3).includes(_4)).isFalse();
    }

    @Test
    void includesShouldReturnTrueWhenLowBoundValue() {
        assertThat(new UidRange(_2, _3).includes(_2)).isTrue();
    }

    @Test
    void includesShouldReturnTrueWhenHighBoundValue() {
        assertThat(new UidRange(_2, _3).includes(_3)).isTrue();
    }

    @Test
    void includesShouldReturnTrueWhenInRange() {
        assertThat(new UidRange(_2, _4).includes(_3)).isTrue();
    }
    
    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(UidRange.class).verify();
    }
    
    @Test
    void toMessageRangeShouldContainSameValues() {
        assertThat(new UidRange(_1, _4).toMessageRange()).isEqualTo(MessageRange.range(_1, _4));
    }
    
    @Test
    void formattedStringShouldUseImapRangeNotationForRange() {
        assertThat(new UidRange(_1, _3).getFormattedString()).isEqualTo("1:3");
    }
    
    @Test
    void formattedStringShouldUseImapRangeNotationForSingleton() {
        assertThat(new UidRange(_2).getFormattedString()).isEqualTo("2");
    }

    @Test
    void shouldBeIterable() {
        assertThat(new UidRange(_1, _4)).containsExactly(_1, _2, _3, _4);
    }
    
    @Test
    void mergeZeroRangeShouldOutputZeroRange() {
        assertThat(UidRange.mergeRanges(ImmutableList.of())).isEmpty();
    }

    @Test
    void mergeSingleRangeShouldOutputSameRange() {
        List<UidRange> actual = UidRange.mergeRanges(ImmutableList.of(new UidRange(_1, _4)));
        assertThat(actual).containsOnly(new UidRange(_1, _4));
    }

    @Test
    void mergeContiguousRangeShouldOutputMergedRange() {
        List<UidRange> actual = UidRange
                .mergeRanges(
                        ImmutableList.of(
                                new UidRange(_1, _2),
                                new UidRange(_3, _4)));
        assertThat(actual).containsOnly(new UidRange(_1, _4));
    }
    

    @Test
    void mergeContiguousRangesShouldOutputMergedRange() {
        List<UidRange> actual = UidRange
                .mergeRanges(
                        ImmutableList.of(
                                new UidRange(_1, _2),
                                new UidRange(_1, _5),
                                new UidRange(_3, _4),
                                new UidRange(_2, _10)));
        assertThat(actual).containsOnly(new UidRange(_1, _10));
    }
    
    @Test
    void mergeOverlappingRangeShouldOutputMergedRange() {
        List<UidRange> actual = UidRange
                .mergeRanges(
                        ImmutableList.of(
                                new UidRange(_1, _3),
                                new UidRange(_3, _4)));
        assertThat(actual).containsOnly(new UidRange(_1, _4));
    }
    
    @Test
    void mergeShouldNotMergeRangeWithDistanceGreaterThen1() {
        List<UidRange> actual = UidRange
                .mergeRanges(
                        ImmutableList.of(
                                new UidRange(_1, _2),
                                new UidRange(_4, _5)));
        assertThat(actual).containsOnly(new UidRange(_1, _2), new UidRange(_4, _5));
    }
    

    @Test
    void mergeShouldMergeRangeWhenOverlappingRangesAreNotSorted() {
        List<UidRange> actual = UidRange
                .mergeRanges(
                        ImmutableList.of(
                                new UidRange(_1, _2),
                                new UidRange(_5, _10),
                                new UidRange(_2, _3)));
        assertThat(actual).containsOnly(new UidRange(_1, _3), new UidRange(_5, _10));
    }

}
