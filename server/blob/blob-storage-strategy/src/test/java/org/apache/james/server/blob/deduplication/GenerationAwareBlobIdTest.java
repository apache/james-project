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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.apache.commons.configuration2.PropertiesConfiguration;
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

class GenerationAwareBlobIdTest {
    private static final Instant NOW = Instant.parse("2021-08-19T10:15:30.00Z");

    private BlobId.Factory delegate;
    private UpdatableTickingClock clock;
    private GenerationAwareBlobId.Factory testee;

    @BeforeEach
    void setUp() {
        delegate = new PlainBlobId.Factory();
        clock = new UpdatableTickingClock(NOW);
        testee = new GenerationAwareBlobId.Factory(clock, delegate, GenerationAwareBlobId.Configuration.DEFAULT);
    }

    @Nested
    class BlobIdGeneration {
        @Test
        void ofShouldGenerateABlobIdOfTheRightGeneration() {
            String key = UUID.randomUUID().toString();
            GenerationAwareBlobId actual = testee.of(key);

            SoftAssertions.assertSoftly(soft -> {
                soft.assertThat(actual.getFamily()).isEqualTo(GenerationAwareBlobId.Configuration.DEFAULT.getFamily());
                soft.assertThat(actual.getGeneration()).isEqualTo(628L);
                soft.assertThat(actual.getDelegate()).isEqualTo(delegate.of(key));
            });
        }
    }

    @Nested
    class BlobIdParsing {
        @Test
        void previousBlobIdsShouldBeParsable() {
            String blobIdString = delegate.of("abc").asString();

            GenerationAwareBlobId actual = testee.parse(blobIdString);

            SoftAssertions.assertSoftly(soft -> {
                soft.assertThat(actual.getFamily()).isEqualTo(0);
                soft.assertThat(actual.getGeneration()).isEqualTo(0L);
                soft.assertThat(actual.getDelegate().asString()).isEqualTo(blobIdString);
            });
        }

        @Test
        void noFamilyShouldBeParsable() {
            String blobIdString = "0_0_" + delegate.of("abc").asString();

            GenerationAwareBlobId actual = testee.parse(blobIdString);

            SoftAssertions.assertSoftly(soft -> {
                soft.assertThat(actual.getFamily()).isEqualTo(0);
                soft.assertThat(actual.getGeneration()).isEqualTo(0L);
                soft.assertThat(actual.getDelegate()).isEqualTo(delegate.of("abc"));
            });
        }

        @Test
        void generationBlobIdShouldBeParsable() {
            String blobIdString = "12_126_" + delegate.of("abc").asString();

            GenerationAwareBlobId actual = testee.parse(blobIdString);

            SoftAssertions.assertSoftly(soft -> {
                soft.assertThat(actual.getFamily()).isEqualTo(12);
                soft.assertThat(actual.getGeneration()).isEqualTo(126L);
                soft.assertThat(actual.getDelegate()).isEqualTo(delegate.of("abc"));
            });
        }

        @Test
        void wrappedBlobIdCanContainSeparator() {
            String blobIdString = "12_126_ab_c";

            GenerationAwareBlobId actual = testee.parse(blobIdString);

            SoftAssertions.assertSoftly(soft -> {
                soft.assertThat(actual.getFamily()).isEqualTo(12);
                soft.assertThat(actual.getGeneration()).isEqualTo(126L);
                soft.assertThat(actual.getDelegate()).isEqualTo(delegate.parse("ab_c"));
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "abc",
            "abc_",
            "1_abc",
            "1_2",
            "1_2_",
            "_abc"
        })
        void fromShouldFallbackWhenNotApplicable(String blobIdString) {
            GenerationAwareBlobId actual = testee.parse(blobIdString);

            SoftAssertions.assertSoftly(soft -> {
                soft.assertThat(actual.getFamily()).isEqualTo(0);
                soft.assertThat(actual.getGeneration()).isEqualTo(0L);
                soft.assertThat(actual.getDelegate()).isEqualTo(delegate.parse(blobIdString));
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
                "invalid_2_abc",
                "1_invalid_abc",
                "1__abc",
                "__abc",
                "_1_abc",
                "-1_2_abc",
                "1_-1_abc",
            })
            void fromShouldFallbackWhenNotApplicable(String blobIdString) {
                assertThatThrownBy(() -> testee.parse(blobIdString))
                    .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    @Nested
    class BlobIdPojo {
        @Test
        void shouldMatchPojoContract() {
            EqualsVerifier.forClass(GenerationAwareBlobId.class)
                .verify();
        }

        @Test
        void asStringShouldIntegrateFamilyAndGeneration() {
            BlobId blobId = new GenerationAwareBlobId(23, 456, delegate.parse("abc"));

            assertThat(blobId.asString()).isEqualTo("456_23_abc");
        }

        @Test
        void asStringShouldReturnDelegateForZeroFamily() {
            BlobId blobId = new GenerationAwareBlobId(0, 0, delegate.parse("abc"));

            assertThat(blobId.asString()).isEqualTo("abc");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "1_2_abc",
            "abc"
        })
        void asStringShouldRevertFromString(String blobIdString) {
            GenerationAwareBlobId blobId = testee.parse(blobIdString);

            assertThat(blobId.asString()).isEqualTo(blobIdString);
        }

        @Test
        void noGenerationShouldNeverBeInActiveGeneration() {
            GenerationAwareBlobId blobId = new GenerationAwareBlobId(0, 0, delegate.parse("abc"));

            assertThat(blobId.inActiveGeneration(GenerationAwareBlobId.Configuration.DEFAULT, NOW)).isFalse();
        }

        @Test
        void inActiveGenerationShouldReturnTrueWhenSameDate() {
            GenerationAwareBlobId blobId = testee.of("abc");

            assertThat(blobId.inActiveGeneration(GenerationAwareBlobId.Configuration.DEFAULT, NOW)).isTrue();
        }

        @Test
        void inActiveGenerationShouldReturnTrueWhenInTheFuture() {
            GenerationAwareBlobId blobId = testee.of("abc");

            assertThat(blobId.inActiveGeneration(GenerationAwareBlobId.Configuration.DEFAULT, NOW.minus(60, ChronoUnit.DAYS)))
                .isTrue();
        }

        @Test
        void inActiveGenerationShouldReturnTrueForAtLeastOneMoreMonth() {
            GenerationAwareBlobId blobId = testee.of("abc");

            assertThat(blobId.inActiveGeneration(GenerationAwareBlobId.Configuration.DEFAULT, NOW.plus(30, ChronoUnit.DAYS)))
                .isTrue();
        }

        @Test
        void inActiveGenerationShouldReturnFalseAfterTwoMonth() {
            GenerationAwareBlobId blobId = testee.of("abc");

            assertThat(blobId.inActiveGeneration(GenerationAwareBlobId.Configuration.DEFAULT, NOW.plus(60, ChronoUnit.DAYS)))
                .isFalse();
        }


        @Test
        void inActiveGenerationShouldReturnFalseWhenDistinctFamily() {
            GenerationAwareBlobId blobId = new GenerationAwareBlobId(628L, 2, delegate.of("abcd"));

            assertThat(blobId.inActiveGeneration(GenerationAwareBlobId.Configuration.DEFAULT, NOW.plus(60, ChronoUnit.DAYS)))
                .isFalse();
        }

    }

    @Nested
    class ConfigurationPojo {
        @Test
        void shouldMatchPojoContract() {
            EqualsVerifier.forClass(GenerationAwareBlobId.Configuration.class)
                .verify();
        }

        @Test
        void parseShouldReturnCorrectConfiguration() {
            PropertiesConfiguration configuration = new PropertiesConfiguration();
            configuration.addProperty("deduplication.gc.generation.duration", "15day");
            configuration.addProperty("deduplication.gc.generation.family", "2");


            assertThat(GenerationAwareBlobId.Configuration.parse(configuration))
                .isEqualTo(GenerationAwareBlobId.Configuration.builder()
                    .duration(Duration.ofDays(15))
                    .family(2));
        }

        @Test
        void defaultDurationUnitShouldBeDays() {
            PropertiesConfiguration configuration = new PropertiesConfiguration();
            configuration.addProperty("deduplication.gc.generation.duration", "15");
            configuration.addProperty("deduplication.gc.generation.family", "2");


            assertThat(GenerationAwareBlobId.Configuration.parse(configuration))
                .isEqualTo(GenerationAwareBlobId.Configuration.builder()
                    .duration(Duration.ofDays(15))
                    .family(2));
        }

        @Test
        void parseShouldReturnDefaultWhenNone() {
            PropertiesConfiguration configuration = new PropertiesConfiguration();

            assertThat(GenerationAwareBlobId.Configuration.parse(configuration))
                .isEqualTo(GenerationAwareBlobId.Configuration.DEFAULT);
        }

        @Nested
        class Failures {
            @Test
            void shouldThrowOnZeroFamily() {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("deduplication.gc.generation.duration", "15");
                configuration.addProperty("deduplication.gc.generation.family", "0");


                assertThatThrownBy(() -> GenerationAwareBlobId.Configuration.parse(configuration))
                    .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            void shouldThrowOnNegativeFamily() {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("deduplication.gc.generation.duration", "15");
                configuration.addProperty("deduplication.gc.generation.family", "-1");


                assertThatThrownBy(() -> GenerationAwareBlobId.Configuration.parse(configuration))
                    .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            void shouldThrowOnZeroDuration() {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("deduplication.gc.generation.duration", "0");
                configuration.addProperty("deduplication.gc.generation.family", "1");


                assertThatThrownBy(() -> GenerationAwareBlobId.Configuration.parse(configuration))
                    .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            void shouldThrowOnNegativeDuration() {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("deduplication.gc.generation.duration", "-5day");
                configuration.addProperty("deduplication.gc.generation.family", "1");


                assertThatThrownBy(() -> GenerationAwareBlobId.Configuration.parse(configuration))
                    .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }
}