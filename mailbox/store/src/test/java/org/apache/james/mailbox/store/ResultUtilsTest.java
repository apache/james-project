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

package org.apache.james.mailbox.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.FetchGroup.Profile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ResultUtilsTest {
    static Stream<Arguments> haveValidContent() {
        return Stream.of(
            Arguments.of(FetchGroup.MINIMAL),
            Arguments.of(FetchGroup.MINIMAL.with(Profile.MIME_DESCRIPTOR)),
            Arguments.of(FetchGroup.FULL_CONTENT),
            Arguments.of(FetchGroup.HEADERS),
            Arguments.of(FetchGroup.BODY_CONTENT),
            Arguments.of(FetchGroup.BODY_CONTENT.with(Profile.HEADERS)));
    }

    @ParameterizedTest
    @MethodSource
    void haveValidContent(FetchGroup fetchGroup) {
        assertThat(ResultUtils.haveValidContent(fetchGroup)).isTrue();
    }

    static Stream<Arguments> haveInvalidContent() {
        return Stream.of(
            Arguments.of(FetchGroup.MINIMAL.with(Profile.MIME_CONTENT)),
            Arguments.of(FetchGroup.MINIMAL.with(Profile.MIME_HEADERS)));
    }

    @ParameterizedTest
    @MethodSource
    void haveInvalidContent(FetchGroup fetchGroup) {
        assertThat(ResultUtils.haveValidContent(fetchGroup)).isFalse();
    }

}