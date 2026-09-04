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

package org.apache.james.blob.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BlobIdEntropyTest {
    @Test
    void parseShouldReturnDefaultWhenNotSet() {
        assertThat(BlobIdEntropy.parse(null)).isEqualTo(BlobIdEntropy.DEFAULT_ENTROPY_BITS);
    }

    @Test
    void parseShouldReturnDefaultWhenBlank() {
        assertThat(BlobIdEntropy.parse("  ")).isEqualTo(BlobIdEntropy.DEFAULT_ENTROPY_BITS);
    }

    @Test
    void parseShouldAcceptTruncatedValue() {
        assertThat(BlobIdEntropy.parse("128")).isEqualTo(128);
    }

    @Test
    void parseShouldRejectNonNumericValue() {
        assertThatThrownBy(() -> BlobIdEntropy.parse("many"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseShouldRejectValueThatIsNotAByteCount() {
        assertThatThrownBy(() -> BlobIdEntropy.parse("130"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseShouldRejectValueBelowTheSafetyFloor() {
        assertThatThrownBy(() -> BlobIdEntropy.parse("96"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseShouldRejectValueAboveTheHashLength() {
        assertThatThrownBy(() -> BlobIdEntropy.parse("512"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void randomBytesShouldHonourEntropyLength() {
        assertThat(BlobIdEntropy.randomBytes()).hasSize(BlobIdEntropy.entropyBytes());
    }

    @Test
    void randomBytesShouldNotRepeatItself() {
        assertThat(BlobIdEntropy.randomBytes()).isNotEqualTo(BlobIdEntropy.randomBytes());
    }

    @Test
    void truncateShouldKeepLeadingBytes() {
        byte[] hash = new byte[BlobIdEntropy.entropyBytes() + 4];
        for (int i = 0; i < hash.length; i++) {
            hash[i] = (byte) i;
        }

        byte[] truncated = BlobIdEntropy.truncate(hash);

        assertThat(truncated).hasSize(BlobIdEntropy.entropyBytes());
        for (int i = 0; i < truncated.length; i++) {
            assertThat(truncated[i]).isEqualTo((byte) i);
        }
    }

    @Test
    void truncateShouldLeaveShorterHashesUntouched() {
        byte[] hash = new byte[] {1, 2, 3};

        assertThat(BlobIdEntropy.truncate(hash)).isEqualTo(hash);
    }
}
