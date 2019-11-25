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
    void orShouldReturnAFetchGroupWithUpdatedContent() {
        int expected = FetchGroup.HEADERS_MASK | FetchGroup.FULL_CONTENT_MASK;
        assertThat(FetchGroup.HEADERS.or(FetchGroup.FULL_CONTENT_MASK))
            .isEqualTo(new FetchGroup(expected));
    }

    @Test
    void addPartContentShouldAddPartContentWhenNotYetSpecified() {
        int[] path = {12};
        assertThat(
            FetchGroup.MINIMAL
                .addPartContent(new MimePath(path), FetchGroup.MINIMAL_MASK))
            .isEqualTo(new FetchGroup(FetchGroup.MINIMAL_MASK, ImmutableSet.of(new PartContentDescriptor(FetchGroup.MINIMAL_MASK, new MimePath(path)))));
    }

    @Test
    void addPartContentShouldUnionDifferentPartContents() {
        int[] path = {12};
        int[] path2 = {13};
        assertThat(
            FetchGroup.MINIMAL
                .addPartContent(new MimePath(path), FetchGroup.MINIMAL_MASK)
                .addPartContent(new MimePath(path2), FetchGroup.MINIMAL_MASK))
            .isEqualTo(new FetchGroup(FetchGroup.MINIMAL_MASK,
                ImmutableSet.of(new PartContentDescriptor(FetchGroup.MINIMAL_MASK, new MimePath(path)),
                    new PartContentDescriptor(FetchGroup.MINIMAL_MASK, new MimePath(path2)))));
    }

    @Test
    void addPartContentShouldModifyPartContentWhenAlreadySpecified() {
        int[] path = {12};
        assertThat(
            FetchGroup.MINIMAL
                .addPartContent(new MimePath(path), FetchGroup.MINIMAL_MASK)
                .addPartContent(new MimePath(path), FetchGroup.HEADERS_MASK))
            .isEqualTo(new FetchGroup(FetchGroup.MINIMAL_MASK, ImmutableSet.of(new PartContentDescriptor(FetchGroup.MINIMAL_MASK, new MimePath(path)).or(FetchGroup.HEADERS_MASK))));
    }
}