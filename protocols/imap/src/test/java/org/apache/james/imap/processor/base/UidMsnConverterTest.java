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

package org.apache.james.imap.processor.base;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.NullableMessageSequenceNumber;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class UidMsnConverterTest {
    private UidMsnConverter testee;
    private MessageUid messageUid1;
    private MessageUid messageUid2;
    private MessageUid messageUid3;
    private MessageUid messageUid4;

    @BeforeEach
    void setUp() {
        testee = new UidMsnConverter();
        messageUid1 = MessageUid.of(1);
        messageUid2 = MessageUid.of(2);
        messageUid3 = MessageUid.of(3);
        messageUid4 = MessageUid.of(4);
    }

    @Test
    void getUidShouldReturnEmptyIfNoMessageWithTheGivenMessageNumber() {
        assertThat(testee.getUid(1))
            .isEmpty();
    }

    @Test
    void getUidShouldReturnEmptyIfZero() {
        assertThat(testee.getUid(0))
            .isEmpty();
    }

    @Test
    void loopingGetMSNShouldSucceedForAMillionItems() {
        int count = 1000;
        testee.addAll(IntStream.range(0, count)
            .mapToObj(i -> MessageUid.of(i + 1))
            .collect(Collectors.toList()));

        IntStream.range(0, 1000000)
            .forEach(i -> testee.getMsn(MessageUid.of(i + 1)));
    }

    @Test
    void getUidShouldTheCorrespondingUidIfItExist() {
        testee.addUid(messageUid1);

        assertThat(testee.getUid(1))
            .contains(messageUid1);
    }

    @Test
    void getFirstUidShouldReturnEmptyIfNoMessage() {
        assertThat(testee.getFirstUid()).isEmpty();
    }

    @Test
    void getLastUidShouldReturnEmptyIfNoMessage() {
        assertThat(testee.getLastUid()).isEmpty();
    }

    @Test
    void getFirstUidShouldReturnFirstUidIfAtLeastOneMessage() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);

        assertThat(testee.getFirstUid()).contains(messageUid1);
    }

    @Test
    void getLastUidShouldReturnLastUidIfAtLeastOneMessage() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);

        assertThat(testee.getLastUid()).contains(messageUid2);
    }

    @Test
    void getMsnShouldReturnAbsentIfNoCorrespondingMessage() {
        testee.addUid(messageUid1);

        assertThat(testee.getMsn(messageUid2)).isEqualTo(NullableMessageSequenceNumber.noMessage());
    }

    @Test
    void getMsnShouldReturnMessageNumberIfUidIsThere() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);

        assertThat(testee.getMsn(messageUid2))
            .isEqualTo(NullableMessageSequenceNumber.of(2));
    }

    @Test
    void getNumMessageShouldReturnZeroIfNoMapping() {
        assertThat(testee.getNumMessage())
            .isEqualTo(0);
    }

    @Test
    void getNumMessageShouldReturnTheNumOfMessage() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);

        assertThat(testee.getNumMessage())
            .isEqualTo(2);
    }

    @Test
    void isEmptyShouldReturnTrueIfNoMapping() {
        assertThat(testee.isEmpty())
            .isTrue();
    }

    @Test
    void isEmptyShouldReturnFalseIfMapping() {
        testee.addUid(messageUid1);

        assertThat(testee.isEmpty())
            .isFalse();
    }

    @Test
    void clearShouldClearMapping() {
        testee.addUid(messageUid1);

        testee.clear();

        assertThat(testee.isEmpty())
            .isTrue();
    }

    @Test
    void addUidShouldKeepMessageNumberContiguous() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.addUid(messageUid4);
        testee.addUid(messageUid2);

        assertThat(mapTesteeInternalDataToMsnByUid())
            .isEqualTo(ImmutableMap.of(
                1, messageUid1,
                2, messageUid2,
                3, messageUid3,
                4, messageUid4));
    }

    @Test
    void addUidShouldNotOverridePreviousMapping() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.addUid(messageUid2);

        assertThat(testee.getMsn(messageUid2))
            .isEqualTo(NullableMessageSequenceNumber.of(2));
    }

    @Test
    void removeShouldKeepAMonoticMSNToUIDConversionMappingWhenDeletingBeginning() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.addUid(messageUid4);

        testee.remove(messageUid1);

        assertThat(mapTesteeInternalDataToMsnByUid())
            .isEqualTo(ImmutableMap.of(
                1, messageUid2,
                2, messageUid3,
                3, messageUid4));
    }

    @Test
    void removeShouldKeepAMonoticMSNToUIDConversionMappingWhenDeletingEnd() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.addUid(messageUid4);

        testee.remove(messageUid4);

        assertThat(mapTesteeInternalDataToMsnByUid())
            .isEqualTo(ImmutableMap.of(
                1, messageUid1,
                2, messageUid2,
                3, messageUid3));
    }

    @Test
    void removeShouldKeepAMonoticMSNToUIDConversionMappingWhenDeletingMiddle() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.addUid(messageUid4);

        testee.remove(messageUid3);

        assertThat(mapTesteeInternalDataToMsnByUid())
            .isEqualTo(ImmutableMap.of(
                1, messageUid1,
                2, messageUid2,
                3, messageUid4));
    }

    @Test
    void addUidShouldSupportOutOfOrderUpdates() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid3);
        testee.addUid(messageUid2);
        testee.addUid(messageUid4);

        assertThat(mapTesteeInternalDataToMsnByUid().entrySet())
            .containsExactlyElementsOf(ImmutableMap.of(
                1, messageUid1,
                2, messageUid2,
                3, messageUid3,
                4, messageUid4).entrySet());
    }

    @Test
    void addUidShouldLeadToMonoticMSNToUIDConversionWhenInsertInFirstPosition() {
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);
        testee.addUid(messageUid4);
        testee.addUid(messageUid1);

        assertThat(mapTesteeInternalDataToMsnByUid().entrySet())
            .containsExactlyElementsOf(ImmutableMap.of(
                1, messageUid1,
                2, messageUid2,
                3, messageUid3,
                4, messageUid4).entrySet());
    }

    @Test
    void addAllShouldLeadToMonoticMSNToUIDConversion() {
        testee.addAll(ImmutableList.of(
            messageUid1,
            messageUid2,
            messageUid3,
            messageUid4));

        assertThat(mapTesteeInternalDataToMsnByUid().entrySet())
            .containsExactlyElementsOf(ImmutableMap.of(
                1, messageUid1,
                2, messageUid2,
                3, messageUid3,
                4, messageUid4).entrySet());
    }

    @Test
    void addAllShouldRemoveDuplicates() {
        testee.addUid(messageUid2);
        testee.addAll(ImmutableList.of(
            messageUid1,
            messageUid2,
            messageUid3,
            messageUid4));

        assertThat(mapTesteeInternalDataToMsnByUid().entrySet())
            .containsExactlyElementsOf(ImmutableMap.of(
                1, messageUid1,
                2, messageUid2,
                3, messageUid3,
                4, messageUid4).entrySet());
    }

    @Test
    void addAllShouldDeduplicateElements() {
        testee.addUid(messageUid1);

        testee.addAll(ImmutableList.of(
            messageUid1,
            messageUid2,
            messageUid3,
            messageUid4));

        assertThat(mapTesteeInternalDataToMsnByUid().entrySet())
            .containsExactlyElementsOf(ImmutableMap.of(
                1, messageUid1,
                2, messageUid2,
                3, messageUid3,
                4, messageUid4).entrySet());
    }

    @Test
    void addAllShouldMergeWithPreviousData() {
        testee.addUid(messageUid1);

        testee.addAll(ImmutableList.of(messageUid2,
            messageUid3,
            messageUid4));

        assertThat(mapTesteeInternalDataToMsnByUid().entrySet())
            .containsExactlyElementsOf(ImmutableMap.of(
                1, messageUid1,
                2, messageUid2,
                3, messageUid3,
                4, messageUid4).entrySet());
    }

    @Test
    void addAllShouldMergeAndDeduplicatePreviousData() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid3);

        testee.addAll(ImmutableList.of(messageUid2,
            messageUid3,
            messageUid4));

        assertThat(mapTesteeInternalDataToMsnByUid().entrySet())
            .containsExactlyElementsOf(ImmutableMap.of(
                1, messageUid1,
                2, messageUid2,
                3, messageUid3,
                4, messageUid4).entrySet());
    }

    @Test
    void addAllWithOutOfOrderIteratorShouldLeadToMonoticMSNToUIDConversion() {
        testee.addAll(ImmutableList.of(
            messageUid2,
            messageUid3,
            messageUid4,
            messageUid1));

        assertThat(mapTesteeInternalDataToMsnByUid().entrySet())
            .containsExactlyElementsOf(ImmutableMap.of(
                1, messageUid1,
                2, messageUid2,
                3, messageUid3,
                4, messageUid4).entrySet());
    }

    @Test
    void addUidShouldBeIdempotent() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid1);

        assertThat(mapTesteeInternalDataToMsnByUid())
            .isEqualTo(ImmutableMap.of(1, messageUid1));
    }

    @Test
    void removeShouldBeIdempotent() {
        testee.addUid(messageUid1);
        testee.addUid(messageUid2);
        testee.addUid(messageUid3);

        testee.remove(messageUid2);
        testee.remove(messageUid2);

        assertThat(mapTesteeInternalDataToMsnByUid())
            .isEqualTo(ImmutableMap.of(1, messageUid1,
                2, messageUid3));
    }

    @Test
    void addUidShouldSupportIntOverflow() {
        testee.addUid(MessageUid.of(36));
        testee.addUid(MessageUid.of(17));
        testee.addUid(MessageUid.of(Integer.MAX_VALUE + 1L));
        testee.addUid(MessageUid.of(Integer.MAX_VALUE + 2L));
        testee.addUid(MessageUid.of(13));

        assertThat(mapTesteeInternalDataToMsnByUid())
            .isEqualTo(ImmutableMap.of(1, MessageUid.of(13),
                2, MessageUid.of(17),
                3, MessageUid.of(36),
                4, MessageUid.of(Integer.MAX_VALUE + 1L),
                5, MessageUid.of(Integer.MAX_VALUE + 2L)));
    }

    @Test
    void addAllShouldSupportIntOverflow() {
        testee.addAll(ImmutableList.of(MessageUid.of(36),
            MessageUid.of(17),
            MessageUid.of(Integer.MAX_VALUE + 1L),
            MessageUid.of(Integer.MAX_VALUE + 2L),
            MessageUid.of(13)));

        assertThat(mapTesteeInternalDataToMsnByUid())
            .isEqualTo(ImmutableMap.of(1, MessageUid.of(13),
                2, MessageUid.of(17),
                3, MessageUid.of(36),
                4, MessageUid.of(Integer.MAX_VALUE + 1L),
                5, MessageUid.of(Integer.MAX_VALUE + 2L)));
    }

    @Test
    void addAndRemoveShouldLeadToMonoticMSNToUIDConversionWhenMixed() throws Exception {
        int initialCount = 1000;
        for (int i = 1; i <= initialCount; i++) {
            testee.addUid(MessageUid.of(i));
        }

        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> {
                if (threadNumber == 0) {
                    testee.remove(MessageUid.of(step + 1));
                } else {
                    testee.addUid(MessageUid.of(initialCount + step + 1));
                }
            })
            .threadCount(2)
            .operationCount(initialCount)
            .runSuccessfullyWithin(Duration.ofSeconds(10));

        ImmutableMap.Builder<Integer, MessageUid> resultBuilder = ImmutableMap.builder();
        for (int i = 1; i <= initialCount; i++) {
            resultBuilder.put(i, MessageUid.of(initialCount + i));
        }
        assertThat(mapTesteeInternalDataToMsnByUid().entrySet())
            .containsExactlyElementsOf(resultBuilder.build().entrySet());
    }

    @Test
    void addShouldLeadToMonoticMSNToUIDConversionWhenConcurrent() throws Exception {
        int operationCount = 1000;
        int threadCount = 2;

        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> testee.addUid(MessageUid.of((threadNumber * operationCount) + (step + 1))))
            .threadCount(threadCount)
            .operationCount(operationCount)
            .runSuccessfullyWithin(Duration.ofSeconds(10));

        ImmutableMap.Builder<Integer, MessageUid> resultBuilder = ImmutableMap.builder();
        for (int i = 1; i <= threadCount * operationCount; i++) {
            resultBuilder.put(i, MessageUid.of(i));
        }
        assertThat(mapTesteeInternalDataToMsnByUid().entrySet())
            .containsExactlyElementsOf(resultBuilder.build().entrySet());
    }

    @Test
    void removeShouldLeadToMonoticMSNToUIDConversionWhenConcurrent() throws Exception {
        int operationCount = 1000;
        int threadCount = 2;
        for (int i = 1; i <= operationCount * (threadCount + 1); i++) {
            testee.addUid(MessageUid.of(i));
        }

        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> testee.remove(MessageUid.of((threadNumber * operationCount) + (step + 1))))
            .threadCount(threadCount)
            .operationCount(operationCount)
            .runSuccessfullyWithin(Duration.ofSeconds(10));

        ImmutableMap.Builder<Integer, MessageUid> resultBuilder = ImmutableMap.builder();
        for (int i = 1; i <= operationCount; i++) {
            resultBuilder.put(i, MessageUid.of((threadCount * operationCount) + i));
        }
        assertThat(mapTesteeInternalDataToMsnByUid().entrySet())
            .containsExactlyElementsOf(resultBuilder.build().entrySet());
    }

    private Map<Integer, MessageUid> mapTesteeInternalDataToMsnByUid() {
        ImmutableMap.Builder<Integer, MessageUid> result = ImmutableMap.builder();
        if (testee.usesInts) {
            for (int i = 0; i < testee.uidsAsInts.size(); i++) {
                result.put(i + 1, MessageUid.of(testee.uidsAsInts.get(i)));
            }
            return result.build();
        }
        for (int i = 0; i < testee.uids.size(); i++) {
            result.put(i + 1, MessageUid.of(testee.uids.get(i)));
        }
        return result.build();
    }

}