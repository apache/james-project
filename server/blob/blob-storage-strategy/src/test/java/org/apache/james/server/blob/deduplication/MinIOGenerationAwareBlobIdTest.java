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

package org.apache.james.server.blob.deduplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.utils.UpdatableTickingClock;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import nl.jqno.equalsverifier.EqualsVerifier;

class MinIOGenerationAwareBlobIdTest {
    private static final Instant NOW = Instant.parse("2021-08-19T10:15:30.00Z");

    private BlobId.Factory delegate;
    private MinIOGenerationAwareBlobId.Factory testee;

    @BeforeEach
    void setUp() {
        delegate = new PlainBlobId.Factory();
        UpdatableTickingClock clock = new UpdatableTickingClock(NOW);
        testee = new MinIOGenerationAwareBlobId.Factory(clock, GenerationAwareBlobId.Configuration.DEFAULT, delegate);
    }

    @Nested
    class BlobIdGeneration {
        @Test
        void ofShouldGenerateABlobIdOfTheRightGeneration() {
            String key = "4ccb692e-3efb-40e9-8876-4ecfd51ffd4d";
            MinIOGenerationAwareBlobId actual = (MinIOGenerationAwareBlobId) testee.of(key);

            SoftAssertions.assertSoftly(soft -> {
                soft.assertThat(actual.getFamily()).isEqualTo(GenerationAwareBlobId.Configuration.DEFAULT.getFamily());
                soft.assertThat(actual.getGeneration()).isEqualTo(628L);
                soft.assertThat(actual.getDelegate()).isEqualTo(delegate.of("4/c/cb692e-3efb-40e9-8876-4ecfd51ffd4d"));
            });
        }
    }

    @Nested
    class BlobIdParsing {
        @Test
        void asStringValueShouldBeParsable() {
            String key = UUID.randomUUID().toString();
            BlobId minioBlobId = testee.of(key);
            String minioBlobIdAsString = minioBlobId.asString();

            BlobId blobId = testee.parse(minioBlobIdAsString);
            assertThat(blobId).isEqualTo(minioBlobId);
        }

        @Test
        void previousBlobIdsShouldBeParsable() {
            String blobIdString = delegate.of("abcdef").asString();

            BlobId actual = testee.parse(blobIdString);
            assertThat(actual)
                .isInstanceOfSatisfying(GenerationAwareBlobId.class, actualBlobId -> {
                    SoftAssertions.assertSoftly(soft -> {
                        soft.assertThat(actualBlobId.getFamily()).isEqualTo(0);
                        soft.assertThat(actualBlobId.getGeneration()).isEqualTo(0L);
                        soft.assertThat(actualBlobId.getDelegate().asString()).isEqualTo(blobIdString);
                    });
                });
        }

        @Test
        void previousFourFolderDepthBlobIdsShouldBeParsedAsMinIOGenerationAwareBlobId() {
            String fourDepthBlobIdString = delegate.of("1/628/3/6/8/2/5033-d835-4490-9f5a-eef120b1e85c").asString();

            BlobId actual = testee.parse(fourDepthBlobIdString);
            assertThat(actual)
                .isInstanceOfSatisfying(MinIOGenerationAwareBlobId.class, actualBlobId -> {
                    SoftAssertions.assertSoftly(soft -> {
                        soft.assertThat(actualBlobId.getFamily()).isEqualTo(1);
                        soft.assertThat(actualBlobId.getGeneration()).isEqualTo(628L);
                        soft.assertThat(actualBlobId.getDelegate().asString()).isEqualTo("3/6/8/2/5033-d835-4490-9f5a-eef120b1e85c");
                        soft.assertThat(actualBlobId.asString()).isEqualTo("1/628/3/6/8/2/5033-d835-4490-9f5a-eef120b1e85c");
                    });
                });
        }

        @Test
        void noFamilyShouldBeParsable() {
            String originalBlobId = "abcdef";
            String blobIdString = "0/0/" + delegate.of(originalBlobId).asString();

            BlobId actual = testee.parse(blobIdString);
            assertThat(actual)
                .isInstanceOfSatisfying(MinIOGenerationAwareBlobId.class, actualBlobId -> {
                    SoftAssertions.assertSoftly(soft -> {
                        soft.assertThat(actualBlobId.getFamily()).isEqualTo(0);
                        soft.assertThat(actualBlobId.getGeneration()).isEqualTo(0L);
                        soft.assertThat(actualBlobId.getDelegate()).isEqualTo(delegate.of(originalBlobId));
                    });
                });
        }

        @Test
        void generationBlobIdShouldBeParsable() {
            String originalBlobId = "abcdef";
            String blobIdString = "12/126/" + delegate.of(originalBlobId).asString();

            BlobId actual = testee.parse(blobIdString);
            assertThat(actual)
                .isInstanceOfSatisfying(MinIOGenerationAwareBlobId.class, actualBlobId -> {
                    SoftAssertions.assertSoftly(soft -> {
                        soft.assertThat(actualBlobId.getFamily()).isEqualTo(12);
                        soft.assertThat(actualBlobId.getGeneration()).isEqualTo(126L);
                        soft.assertThat(actualBlobId.getDelegate()).isEqualTo(delegate.of(originalBlobId));
                    });
                });
        }

        @Test
        void wrappedBlobIdCanContainSeparator() {
            String blobIdString = "12/126/ab/c";

            BlobId actual = testee.parse(blobIdString);
            assertThat(actual)
                .isInstanceOfSatisfying(MinIOGenerationAwareBlobId.class, actualBlobId -> {
                    SoftAssertions.assertSoftly(soft -> {
                        soft.assertThat(actualBlobId.getFamily()).isEqualTo(12);
                        soft.assertThat(actualBlobId.getGeneration()).isEqualTo(126L);
                        soft.assertThat(actualBlobId.getDelegate()).isEqualTo(delegate.of("ab/c"));
                    });
                });
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "abcdefgh",
            "abcdefgh/",
            "1/abcdefgh",
            "1/2",
            "1/2/",
            "/abcdefgh"
        })
        void fromShouldFallbackWhenNotApplicable(String blobIdString) {
            BlobId actual = testee.parse(blobIdString);
            assertThat(actual)
                .isInstanceOfSatisfying(GenerationAwareBlobId.class, actualBlobId -> {
                    SoftAssertions.assertSoftly(soft -> {
                        soft.assertThat(actualBlobId.getFamily()).isEqualTo(0);
                        soft.assertThat(actualBlobId.getGeneration()).isEqualTo(0L);
                        soft.assertThat(actualBlobId.getDelegate()).isEqualTo(delegate.parse(blobIdString));
                    });
                });
        }

        @Nested
        class Failures {
            @Test
            void emptyShouldFail() {
                assertThatThrownBy(() -> testee.parse(""))
                    .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            void nullShouldFailShouldFail() {
                assertThatThrownBy(() -> testee.parse(null))
                    .isInstanceOf(NullPointerException.class);
            }

            @ParameterizedTest
            @ValueSource(strings = {
                "invalid/2/abcdefgh",
                "1/invalid/abcdefgh",
                "1//abcdefgh",
                "//abcdefgh",
                "/1/abcdefgh",
                "-1/2/abcdefgh",
                "1/-1/abcdefgh",
            })
            void fromShouldFallbackWhenNotApplicable(String blobIdString) {
                assertThatCode(() -> testee.parse(blobIdString))
                    .doesNotThrowAnyException();
            }
        }
    }

    @Nested
    class BlobIdPojo {

        @Test
        void shouldMatchPojoContract() {
            EqualsVerifier.forClass(MinIOGenerationAwareBlobId.class)
                .verify();
        }

        @Test
        void asStringShouldIntegrateFamilyAndGeneration() {
            BlobId blobId = testee.of("36825033-d835-4490-9f5a-eef120b1e85c");

            assertThat(blobId.asString()).isEqualTo("1/628/3/6/825033-d835-4490-9f5a-eef120b1e85c");
        }

        @Test
        void asStringShouldReturnDelegateForZeroFamily() {
            BlobId blobId = new MinIOGenerationAwareBlobId(0, 0, delegate.parse("36825033-d835-4490-9f5a-eef120b1e85c"));

            assertThat(blobId.asString()).isEqualTo("36825033-d835-4490-9f5a-eef120b1e85c");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "1/2/a/b/c/d/efgh",
            "abc",
            "1/2/abc",
            "1/628/3/6/8/2/5033-d835-4490-9f5a-eef120b1e85c"
        })
        void asStringShouldRevertFromString(String blobIdString) {
            BlobId blobId = testee.parse(blobIdString);

            assertThat(blobId.asString()).isEqualTo(blobIdString);
        }

        @Test
        void noGenerationShouldNeverBeInActiveGeneration() {
            MinIOGenerationAwareBlobId blobId = new MinIOGenerationAwareBlobId(0, 0, delegate.parse("36825033-d835-4490-9f5a-eef120b1e85c"));

            assertThat(blobId.inActiveGeneration(GenerationAwareBlobId.Configuration.DEFAULT, NOW)).isFalse();
        }

        @Test
        void inActiveGenerationShouldReturnTrueWhenSameDate() {
            BlobId blobId = testee.of("36825033-d835-4490-9f5a-eef120b1e85c");

            assertThat(blobId)
                .isInstanceOfSatisfying(MinIOGenerationAwareBlobId.class, actualBlobId ->
                    assertThat(actualBlobId.inActiveGeneration(GenerationAwareBlobId.Configuration.DEFAULT, NOW)).isTrue());
        }

        @Test
        void inActiveGenerationShouldReturnTrueWhenInTheFuture() {
            BlobId blobId = testee.of("36825033-d835-4490-9f5a-eef120b1e85c");

            assertThat(blobId)
                .isInstanceOfSatisfying(MinIOGenerationAwareBlobId.class, actualBlobId ->
                    assertThat(actualBlobId.inActiveGeneration(GenerationAwareBlobId.Configuration.DEFAULT, NOW.minus(60, ChronoUnit.DAYS))).isTrue());
        }

        @Test
        void inActiveGenerationShouldReturnTrueForAtLeastOneMoreMonth() {
            BlobId blobId = testee.of("36825033-d835-4490-9f5a-eef120b1e85c");
            assertThat(blobId)
                .isInstanceOfSatisfying(MinIOGenerationAwareBlobId.class, actualBlobId ->
                    assertThat(actualBlobId.inActiveGeneration(GenerationAwareBlobId.Configuration.DEFAULT, NOW.plus(30, ChronoUnit.DAYS))).isTrue());
        }

        @Test
        void inActiveGenerationShouldReturnFalseAfterTwoMonth() {
            BlobId blobId = testee.of("36825033-d835-4490-9f5a-eef120b1e85c");

            assertThat(blobId)
                .isInstanceOfSatisfying(MinIOGenerationAwareBlobId.class, actualBlobId ->
                    assertThat(actualBlobId.inActiveGeneration(GenerationAwareBlobId.Configuration.DEFAULT, NOW.plus(60, ChronoUnit.DAYS))).isFalse());
        }

        @Test
        void inActiveGenerationShouldReturnFalseWhenDistinctFamily() {
            MinIOGenerationAwareBlobId blobId = new MinIOGenerationAwareBlobId(628L, 2, delegate.of("36825033-d835-4490-9f5a-eef120b1e85c"));

            assertThat(blobId.inActiveGeneration(GenerationAwareBlobId.Configuration.DEFAULT, NOW.plus(60, ChronoUnit.DAYS)))
                .isFalse();
        }
    }
}