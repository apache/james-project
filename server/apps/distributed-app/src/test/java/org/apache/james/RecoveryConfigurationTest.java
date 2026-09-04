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

package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class RecoveryConfigurationTest {
    @Test
    void parseShouldReturnEmptyWhenNoDateProvided() {
        assertThat(RecoveryConfiguration.parse(new String[] {}).restoreAfter()).isEmpty();
    }

    @Test
    void parseShouldReadRestoreAfterArgument() {
        assertThat(RecoveryConfiguration.parse(new String[] {"--restore-after=2026-01-01T00:00:00Z"}).restoreAfter())
            .contains(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void parseShouldRejectInvalidInstant() {
        assertThatThrownBy(() -> RecoveryConfiguration.parse(new String[] {"--restore-after=not-a-date"}))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void headerBlobPrefixShouldBeEmptyWhenNoFamily() {
        assertThat(RecoveryConfiguration.parse(new String[] {}).headerBlobPrefix()).isEmpty();
    }

    @Test
    void headerBlobPrefixShouldOnlyCarryFamilyWhenNoGeneration() {
        assertThat(RecoveryConfiguration.parse(new String[] {"--family=1"}).headerBlobPrefix())
            .isEqualTo("1_");
    }

    @Test
    void headerBlobPrefixShouldCarryFamilyAndGeneration() {
        assertThat(RecoveryConfiguration.parse(new String[] {"--family=1", "--generation=690"}).headerBlobPrefix())
            .isEqualTo("1_690_");
    }

    @Test
    void headerBlobPrefixShouldUseSlashWhenMinioSeparator() {
        assertThat(RecoveryConfiguration.parse(new String[] {"--family=1", "--generation=690", "--minio-separator"}).headerBlobPrefix())
            .isEqualTo("1/690/");
    }

    @Test
    void parseShouldRejectGenerationWithoutFamily() {
        assertThatThrownBy(() -> RecoveryConfiguration.parse(new String[] {"--generation=690"}))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseShouldRejectNonNumericFamily() {
        assertThatThrownBy(() -> RecoveryConfiguration.parse(new String[] {"--family=one"}))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseShouldRejectNonPositiveFamily() {
        assertThatThrownBy(() -> RecoveryConfiguration.parse(new String[] {"--family=0"}))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseShouldRejectNonNumericGeneration() {
        assertThatThrownBy(() -> RecoveryConfiguration.parse(new String[] {"--family=1", "--generation=latest"}))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseShouldDefaultConcurrency() {
        assertThat(RecoveryConfiguration.parse(new String[] {}).concurrency())
            .isEqualTo(RecoveryConfiguration.DEFAULT_CONCURRENCY);
    }

    @Test
    void parseShouldReadConcurrencyArgument() {
        assertThat(RecoveryConfiguration.parse(new String[] {"--concurrency=32"}).concurrency())
            .isEqualTo(32);
    }

    @Test
    void parseShouldRejectNonPositiveConcurrency() {
        assertThatThrownBy(() -> RecoveryConfiguration.parse(new String[] {"--concurrency=0"}))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseShouldRejectNonNumericConcurrency() {
        assertThatThrownBy(() -> RecoveryConfiguration.parse(new String[] {"--concurrency=many"}))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
