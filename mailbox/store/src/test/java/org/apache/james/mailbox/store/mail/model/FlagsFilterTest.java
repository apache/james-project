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

import java.util.List;

import jakarta.mail.Flags;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class FlagsFilterTest {

    List<Flags.Flag> listOfFlags = ImmutableList.of(Flags.Flag.SEEN, Flags.Flag.RECENT, Flags.Flag.FLAGGED);
    List<String> listOfUserFlags = ImmutableList.of("VeryImportant", "Bof");

    @Test
    void buildShouldNotRequireAnyProperty() {
        assertThat(FlagsFilter.builder().build()).isNotNull();
    }

    @Test
    void buildWithoutPropertyShouldReturnNoFilter() {
        FlagsFilter filter = FlagsFilter.builder().build();
        assertThat(listOfFlags.stream().filter(filter.getSystemFlagFilter())).isEqualTo(listOfFlags);
        assertThat(listOfUserFlags.stream().filter(filter.getUserFlagFilter())).isEqualTo(listOfUserFlags);
    }

    @Test
    void buildWithSystemFlagFilterShouldNotFilterUserFlags() {
        FlagsFilter filter = FlagsFilter.builder()
            .systemFlagFilter(flag -> false)
            .build();
        assertThat(listOfUserFlags.stream().filter(filter.getUserFlagFilter())).isEqualTo(listOfUserFlags);
    }

    @Test
    void buildWithUSerFlagFilterShouldNotFilterSystemFlags() {
        FlagsFilter filter = FlagsFilter.builder()
            .userFlagFilter(flag -> false)
            .build();
        assertThat(listOfFlags.stream().filter(filter.getSystemFlagFilter())).isEqualTo(listOfFlags);
    }


    @Test
    void buildWithBothFiltersShouldApplyFilterOnBothFlagTypes() {
        FlagsFilter filter = FlagsFilter.builder()
            .userFlagFilter(flag -> false)
            .systemFlagFilter(flag -> false)
            .build();
        assertThat(listOfFlags.stream().filter(filter.getSystemFlagFilter())).isEmpty();
        assertThat(listOfUserFlags.stream().filter(filter.getUserFlagFilter())).isEmpty();
    }
}