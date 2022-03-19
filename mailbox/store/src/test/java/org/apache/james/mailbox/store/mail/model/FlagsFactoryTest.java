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
package org.apache.james.mailbox.store.mail.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class FlagsFactoryTest {

    ImmutableList<Flag> listOfSystemFlags = ImmutableList.of(Flag.SEEN, Flag.RECENT, Flag.ANSWERED);
    ImmutableList<String> listOfUserFlags = ImmutableList.of("userFlag", "soCool");
    Flags emptyFlags;
    Flags someFlags;

    @BeforeEach
    void setup() {
        emptyFlags = new Flags();
        someFlags = new Flags();
        listOfSystemFlags.forEach(someFlags::add);
        listOfUserFlags.forEach(someFlags::add);
    }

    @Test
    void builderShouldAllowEmptyFactory() {
        assertThat(FlagsFactory.builder().build()).isEqualTo(emptyFlags);
    }

    @Test
    void builderShouldNotRequireFlagsInstanceWhenUserFlagsDefined() {
        Flags actual = FlagsFactory.builder().addUserFlags("userFlag").build();
        assertThat(actual.getUserFlags()).containsOnly("userFlag");
    }

    @Test
    void builderShouldNotRequireUserFlagsWhenFlagsInstanceDefined() {
        assertThat(FlagsFactory.builder().flags(new Flags()).build()).isNotNull();
    }

    @Test
    void builderShouldAcceptNullUserFlags() {
        assertThat(
            FlagsFactory.builder()
                .addUserFlags((String)null)
                .build())
            .isEqualTo(new Flags());
    }

    @Test
    void builderShouldFilterUserFlags() {
        Flags actual = FlagsFactory.builder()
            .flags(someFlags)
            .filteringFlags(
                FlagsFilter
                    .builder()
                    .userFlagFilter(f -> f.equals("soCool"))
                    .build())
            .build();
        assertThat(actual.getUserFlags()).containsOnly("soCool");
    }

    @Test
    void builderShouldFilterSystemFlags() {
        Flags actual = FlagsFactory.builder()
            .flags(someFlags)
            .filteringFlags(
                FlagsFilter
                    .builder()
                    .systemFlagFilter(f -> f.equals(Flag.SEEN))
                    .build())
            .build();
        assertThat(actual.getSystemFlags()).containsOnly(Flag.SEEN);
    }

    @Test
    void builderShouldAllowFilteringOnEmptyFlags() {
        Flags actual = FlagsFactory.builder()
            .flags(emptyFlags)
            .filteringFlags(
                FlagsFilter
                    .builder()
                    .systemFlagFilter(f -> f.equals(Flag.SEEN))
                    .userFlagFilter(f -> f.equals("soCool"))
                    .build())
            .build();
        assertThat(actual).isNotNull();
    }

    @Test
    void builderShouldFilterOnFlags() {
        Flags actual = FlagsFactory.builder()
            .flags(someFlags)
            .filteringFlags(
                FlagsFilter
                    .builder()
                    .systemFlagFilter(f -> f.equals(Flag.SEEN))
                    .userFlagFilter(f -> f.equals("soCool"))
                    .build())
            .build();
        assertThat(actual.getSystemFlags()).containsOnly(Flag.SEEN);
        assertThat(actual.getUserFlags()).containsOnly("soCool");
    }

    @Test
    void builderShouldTrimEmptyUserFlags() {
        Flags flags = new Flags();
        flags.add("");
        flags.add("value2");
        Flags actual = FlagsFactory.builder().flags(flags).addUserFlags("", "value").build();
        assertThat(actual.getUserFlags()).containsOnly("value", "value2");
    }

    @Test
    void builderShouldTrimNullUserFlags() {
        Flags actual = FlagsFactory.builder().addUserFlags(null, "value").build();
        assertThat(actual.getUserFlags()).containsOnly("value");
    }

}