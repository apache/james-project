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

package org.apache.james.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class SizeFormatTest {

    @Test
    void formatShouldThrowWhenNegative() {
        assertThatThrownBy(() -> SizeFormat.format(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatShouldAcceptZero() {
        assertThat(SizeFormat.format(0))
            .isEqualTo("0 bytes");
    }

    @Test
    void formatShouldUseByteWhenAlmostKiB() {
        assertThat(SizeFormat.format(1023))
            .isEqualTo("1023 bytes");
    }

    @Test
    void formatShouldUseKiBWhenExactlyOneKiB() {
        assertThat(SizeFormat.format(1024))
            .isEqualTo("1 KiB");
    }

    @Test
    void formatShouldHaveTwoDigitPrecision() {
        assertThat(SizeFormat.format(1024 + 100))
            .isEqualTo("1.09 KiB");
    }

    @Test
    void formatShouldBeExpressedInKiBWhenAlmostMiB() {
        assertThat(SizeFormat.format(1024 * 1024 - 1))
            .isEqualTo("1023.99 KiB");
    }

    @Test
    void formatShouldKeepTwoDigitPrecisionWhenRoundingDown() {
        assertThat(SizeFormat.format(2 * 1024 * 1024 - 1))
            .isEqualTo("1.99 MiB");
    }

    @Test
    void formatShouldUseMiBWhenExactlyOneMiB() {
        assertThat(SizeFormat.format(1024 * 1024))
            .isEqualTo("1 MiB");
    }

    @Test
    void formatShouldUseGiBWhenExactlyOneGiB() {
        assertThat(SizeFormat.format(1024 * 1024 * 1024))
            .isEqualTo("1 GiB");
    }

    @Test
    void formatShouldUseTiBWhenExactlyOneTiB() {
        assertThat(SizeFormat.format(1024L * 1024L * 1024L * 1024L))
            .isEqualTo("1 TiB");
    }

}
