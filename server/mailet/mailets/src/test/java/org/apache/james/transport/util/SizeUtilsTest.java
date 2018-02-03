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

package org.apache.james.transport.util;

import static org.apache.james.transport.util.SizeUtils.humanReadableSize;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class SizeUtilsTest {

    @Test
    public void humanSizeShouldAddByteUnitForSmallSize() {
        assertThat(humanReadableSize(1000)).isEqualTo("1000 B");
    }

    @Test
    public void humanSizeShouldScaleToKilobyteWhenSizeIsBetweenKilobyteAndMegabyte() {
        assertThat(humanReadableSize(1024)).isEqualTo("1 KiB");
    }

    @Test
    public void humanSizeShouldWorkWithZero() {
        assertThat(humanReadableSize(0)).isEqualTo("0 B");
    }

    @Test
    public void humanSizeShouldWorkWithNegative() {
        assertThat(humanReadableSize(-1)).isEqualTo("-1 B");
    }

    @Test
    public void humanSizeShouldScaleToMegabyteWhenSizeIsBetweenMegabyteAndGigabyte() {
        assertThat(humanReadableSize(1024 * 1024)).isEqualTo("1 MiB");
    }

    @Test
    public void humanSizeShouldScaleToGigabyteWhenSizeIsBiggerThanGigabyte() {
        assertThat(humanReadableSize(1024 * 1024 * 1024)).isEqualTo("1 GiB");
    }

    @Test
    public void humanSizeShouldCorrectlyCountKilobyte() {
        assertThat(humanReadableSize(42 * 1024)).isEqualTo("42 KiB");
    }

    @Test
    public void humanSizeShouldNotUseMoreThanOneDigitAfterComma() {
        assertThat(humanReadableSize(1.42 * 1024)).isEqualTo("1.4 KiB");
    }

    @Test
    public void humanSizeShouldNotUseMoreThanOneDigitAfterCommaAndRoundUpCorrectly() {
        assertThat(humanReadableSize(1.48 * 1024)).isEqualTo("1.5 KiB");
    }
}