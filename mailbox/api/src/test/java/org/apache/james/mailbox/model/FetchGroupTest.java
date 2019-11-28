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

package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.stream.Stream;

import org.apache.james.mailbox.model.FetchGroup.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.collect.ImmutableSet;

import nl.jqno.equalsverifier.EqualsVerifier;

class FetchGroupTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(FetchGroup.class)
            .withNonnullFields("profiles")
            .verify();
    }

    static Stream<Arguments> withShouldAddNewValuesInSet() {
        return Stream.of(
            Arguments.arguments(EnumSet.noneOf(Profile.class), EnumSet.noneOf(Profile.class), EnumSet.noneOf(Profile.class)),
            Arguments.arguments(EnumSet.noneOf(Profile.class), EnumSet.allOf(Profile.class), EnumSet.allOf(Profile.class)),
            Arguments.arguments(EnumSet.allOf(Profile.class), EnumSet.noneOf(Profile.class), EnumSet.allOf(Profile.class)),
            Arguments.arguments(EnumSet.noneOf(Profile.class), EnumSet.of(Profile.BODY_CONTENT, Profile.FULL_CONTENT), EnumSet.of(Profile.BODY_CONTENT, Profile.FULL_CONTENT)),
            Arguments.arguments(EnumSet.of(Profile.BODY_CONTENT), EnumSet.of(Profile.FULL_CONTENT), EnumSet.of(Profile.BODY_CONTENT, Profile.FULL_CONTENT)),
            Arguments.arguments(EnumSet.of(Profile.BODY_CONTENT), EnumSet.of(Profile.BODY_CONTENT), EnumSet.of(Profile.BODY_CONTENT)),
            Arguments.arguments(EnumSet.of(Profile.BODY_CONTENT, Profile.FULL_CONTENT), EnumSet.of(Profile.BODY_CONTENT, Profile.FULL_CONTENT), EnumSet.of(Profile.BODY_CONTENT, Profile.FULL_CONTENT)),
            Arguments.arguments(EnumSet.of(Profile.BODY_CONTENT, Profile.FULL_CONTENT), EnumSet.of(Profile.BODY_CONTENT), EnumSet.of(Profile.BODY_CONTENT, Profile.FULL_CONTENT))
        );
    }

    @ParameterizedTest
    @MethodSource
    void withShouldAddNewValuesInSet(EnumSet<Profile> initial,
                                      EnumSet<Profile> addition,
                                      EnumSet<Profile> expected) {
        FetchGroup fetchGroup = new FetchGroup(initial);
        assertThat(fetchGroup.with(addition).profiles()).isEqualTo(expected);
    }

    @Test
    void addPartContentShouldAddPartContentWhenNotYetSpecified() {
        int[] path = {12};
        assertThat(
            FetchGroup.MINIMAL
                .addPartContent(new MimePath(path), EnumSet.noneOf(Profile.class)))
            .isEqualTo(new FetchGroup(EnumSet.noneOf(Profile.class),
                ImmutableSet.of(new PartContentDescriptor(new MimePath(path)))));
    }

    @Test
    void addPartContentShouldUnionDifferentPartContents() {
        int[] path = {12};
        int[] path2 = {13};
        assertThat(
            FetchGroup.MINIMAL
                .addPartContent(new MimePath(path), EnumSet.noneOf(Profile.class))
                .addPartContent(new MimePath(path2), EnumSet.noneOf(Profile.class)))
            .isEqualTo(new FetchGroup(EnumSet.noneOf(Profile.class),
                ImmutableSet.of(new PartContentDescriptor(new MimePath(path)),
                    new PartContentDescriptor(new MimePath(path2)))));
    }

    @Test
    void addPartContentShouldModifyPartContentWhenAlreadySpecified() {
        int[] path = {12};
        assertThat(
            FetchGroup.MINIMAL
                .addPartContent(new MimePath(path), EnumSet.noneOf(Profile.class))
                .addPartContent(new MimePath(path), EnumSet.of(Profile.HEADERS)))
            .isEqualTo(new FetchGroup(EnumSet.noneOf(Profile.class),
                ImmutableSet.of(new PartContentDescriptor(EnumSet.of(Profile.HEADERS), new MimePath(path)))));
    }
}