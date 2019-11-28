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

import org.apache.james.mailbox.model.FetchGroup.Profile;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import nl.jqno.equalsverifier.EqualsVerifier;

class FetchGroupTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(FetchGroup.class)
            .verify();
    }

    @Test
    void withShouldReturnAFetchGroupWithUpdatedContent() {
        assertThat(FetchGroup.HEADERS.with(Profile.FULL_CONTENT))
            .isEqualTo(new FetchGroup(EnumSet.of(Profile.HEADERS, Profile.FULL_CONTENT)));
    }

    @Test
    void withShouldReturnAFetchGroupWithSameContentWhenNoop() {
        assertThat(FetchGroup.HEADERS.with(Profile.HEADERS))
            .isEqualTo(new FetchGroup(EnumSet.of(Profile.HEADERS, Profile.HEADERS)));
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