/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http: ww.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.store.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.stream.Stream;

import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.FetchGroup.Profile;
import org.apache.james.mailbox.model.MimePath;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FetchGroupConverterTest {
    private static final int[] PARTS = new int[]{12};

    static Stream<Arguments> getFetchTypeShouldReturnCorrectValue() {
        return Stream.of(
            Arguments.arguments(FetchGroup.MINIMAL, FetchType.METADATA),
            Arguments.arguments(FetchGroup.HEADERS, FetchType.HEADERS),
            Arguments.arguments(FetchGroup.HEADERS_WITH_ATTACHMENTS_METADATA, FetchType.ATTACHMENTS_METADATA),
            Arguments.arguments(FetchGroup.BODY_CONTENT, FetchType.FULL),
            Arguments.arguments(FetchGroup.FULL_CONTENT, FetchType.FULL),
            Arguments.arguments(FetchGroup.BODY_CONTENT.with(Profile.HEADERS), FetchType.FULL),
            Arguments.arguments(FetchGroup.MINIMAL.with(Profile.MIME_CONTENT), FetchType.FULL),
            Arguments.arguments(FetchGroup.MINIMAL.with(Profile.MIME_DESCRIPTOR), FetchType.FULL),
            Arguments.arguments(FetchGroup.MINIMAL.with(Profile.MIME_HEADERS), FetchType.FULL),
            Arguments.arguments(FetchGroup.MINIMAL.addPartContent(new MimePath(PARTS), EnumSet.noneOf(Profile.class)), FetchType.FULL),
            Arguments.arguments(FetchGroup.MINIMAL.addPartContent(new MimePath(PARTS), Profile.HEADERS), FetchType.FULL),
            Arguments.arguments(FetchGroup.MINIMAL.addPartContent(new MimePath(PARTS), Profile.BODY_CONTENT), FetchType.FULL),
            Arguments.arguments(FetchGroup.MINIMAL.addPartContent(new MimePath(PARTS), Profile.FULL_CONTENT), FetchType.FULL));
    }

    @ParameterizedTest
    @MethodSource
    void getFetchTypeShouldReturnCorrectValue(FetchGroup fetchGroup, FetchType expected) {
        assertThat(FetchGroupConverter.getFetchType(fetchGroup)).isEqualTo(expected);
    }
}