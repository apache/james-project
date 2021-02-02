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

package org.apache.james.imap.processor.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.imap.message.response.FetchResponse.BodyElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PartialFetchBodyElementTest {
    private static final long NUMBER_OF_OCTETS = 100;

    BodyElement mockBodyElement;

    @BeforeEach
    void setUp() throws Exception {
        mockBodyElement = mock(BodyElement.class);
        when(mockBodyElement.getName()).thenReturn("Name");
    }

    @Test
    void testSizeShouldBeNumberOfOctetsWhenSizeMoreWhenStartIsZero()
            throws Exception {
        final long moreThanNumberOfOctets = NUMBER_OF_OCTETS + 1;
        PartialFetchBodyElement element = new PartialFetchBodyElement(mockBodyElement, 0, NUMBER_OF_OCTETS);
        when(mockBodyElement.size()).thenReturn(moreThanNumberOfOctets);

        assertThat(element.size()).describedAs("Size is more than number of octets so should be number of octets").isEqualTo(NUMBER_OF_OCTETS);
    }

    @Test
    void testSizeShouldBeSizeWhenNumberOfOctetsMoreWhenStartIsZero()
            throws Exception {
        final long lessThanNumberOfOctets = NUMBER_OF_OCTETS - 1;
        PartialFetchBodyElement element = new PartialFetchBodyElement(mockBodyElement, 0, NUMBER_OF_OCTETS);
        when(mockBodyElement.size()).thenReturn(lessThanNumberOfOctets);

        assertThat(element.size()).describedAs("Size is less than number of octets so should be size").isEqualTo(lessThanNumberOfOctets);
    }

    @Test
    void testWhenStartPlusNumberOfOctetsIsMoreThanSizeSizeShouldBeSizeMinusStart()
            throws Exception {
        final long size = 60;
        PartialFetchBodyElement element = new PartialFetchBodyElement(mockBodyElement, 10, NUMBER_OF_OCTETS);
        when(mockBodyElement.size()).thenReturn(size);

        assertThat(element.size()).describedAs("Size is less than number of octets so should be size").isEqualTo(50);
    }

    @Test
    void testWhenStartPlusNumberOfOctetsIsLessThanSizeSizeShouldBeNumberOfOctetsMinusStart()
            throws Exception {
        final long size = 100;
        PartialFetchBodyElement element = new PartialFetchBodyElement(mockBodyElement, 10, NUMBER_OF_OCTETS);
        when(mockBodyElement.size()).thenReturn(size);

        assertThat(element.size()).describedAs("Size is less than number of octets so should be size").isEqualTo(90);
    }

    @Test
    void testSizeShouldBeZeroWhenStartIsMoreThanSize() throws Exception {
        final long size = 100;
        PartialFetchBodyElement element = new PartialFetchBodyElement(mockBodyElement, 1000, NUMBER_OF_OCTETS);
        when(mockBodyElement.size()).thenReturn(size);

        assertThat(element.size()).describedAs("Size is less than number of octets so should be size").isEqualTo(0);
    }

    @Test
    void testSizeShouldBeNumberOfOctetsWhenStartMoreThanOctets()
            throws Exception {
        final long size = 2000;
        PartialFetchBodyElement element = new PartialFetchBodyElement(mockBodyElement, 1000, NUMBER_OF_OCTETS);
        when(mockBodyElement.size()).thenReturn(size);

        assertThat(element.size()).describedAs("Content size is less than start. Size should be zero.").isEqualTo(NUMBER_OF_OCTETS);
    }
}
