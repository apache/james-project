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
package org.apache.james.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Flags;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class ApplicableFlagBuilderTest {

    @Test
    void shouldAtLeastContainAllDefaultApplicativeFlag() {
        assertThat(ApplicableFlagBuilder.builder().build())
            .isEqualTo(ApplicableFlagBuilder.DEFAULT_APPLICABLE_FLAGS);
    }

    @Test
    void shouldNeverRetainRecentAndUserFlag() {
        Flags result = ApplicableFlagBuilder.builder()
            .add(new Flags(Flags.Flag.RECENT))
            .add(new Flags(Flags.Flag.USER))
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result.contains(Flags.Flag.RECENT)).isFalse();
            softly.assertThat(result.contains(Flags.Flag.USER)).isFalse();
        });
    }

    @Test
    void shouldAddCustomUserFlagIfProvidedToDefaultFlag() {
        Flags result = ApplicableFlagBuilder.builder()
            .add("yolo", "vibe")
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result.contains(ApplicableFlagBuilder.DEFAULT_APPLICABLE_FLAGS)).isTrue();
            softly.assertThat(result.contains("yolo")).isTrue();
            softly.assertThat(result.contains("vibe")).isTrue();
        });
    }

    @Test
    void shouldAcceptUserCustomFlagInsideFlags() {
        Flags result = ApplicableFlagBuilder.builder()
            .add(new Flags("yolo"))
            .build();

        assertThat(result.contains("yolo")).isTrue();
    }

    @Test
    void shouldAcceptFlagsThatContainMultipleFlag() {
        Flags flags = FlagsBuilder.builder()
            .add("yolo", "vibes")
            .build();

        Flags result = ApplicableFlagBuilder.builder()
            .add(flags)
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result.contains("yolo")).isTrue();
            softly.assertThat(result.contains("vibes")).isTrue();
        });
    }

    @Test
    void addShouldAddMultipleFlagsAtOnce() {
        Flags flags = new Flags("cartman");
        Flags flags2 = new Flags("butters");
        Flags result = ApplicableFlagBuilder.builder()
                .add(flags, flags2)
                .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result.contains(flags)).isTrue();
            softly.assertThat(result.contains(flags2)).isTrue();
        });
    }

    @Test
    void shouldAcceptMultipleFlagAtOnce() {
        Flags result = ApplicableFlagBuilder.builder()
            .add("cartman", "butters")
            .add("chef", "randy")
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result.contains("cartman")).isTrue();
            softly.assertThat(result.contains("butters")).isTrue();
            softly.assertThat(result.contains("chef")).isTrue();
            softly.assertThat(result.contains("randy")).isTrue();
        });
    }

    @Test
    void shouldAcceptListOfFlags() throws Exception {
        Flags result = ApplicableFlagBuilder.builder()
            .add(ImmutableList.of(new Flags("cartman"), new Flags("chef")))
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result.contains("cartman")).isTrue();
            softly.assertThat(result.contains("chef")).isTrue();
        });
    }
}