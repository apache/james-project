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

package org.apache.james.imap.processor.fetch;

import static org.apache.james.imap.api.message.SectionType.CONTENT;
import static org.apache.james.imap.api.message.SectionType.HEADER;
import static org.apache.james.imap.api.message.SectionType.MIME;
import static org.apache.james.imap.api.message.SectionType.TEXT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.BodyFetchElement;
import org.apache.james.imap.api.message.FetchData;
import org.apache.james.imap.api.message.FetchData.Item;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.FetchGroup.Profile;
import org.apache.james.mailbox.model.MimePath;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FetchDataConverterTest {
    private static final boolean PEEK = true;
    private static final int[] PATH = new int[]{0, 1, 2};

    static Stream<Arguments> getFetchGroupShouldReturnCorrectValue() {
        return Stream.of(
            Arguments.arguments(FetchData.builder(), FetchGroup.MINIMAL),
            Arguments.arguments(FetchData.builder().fetch(Item.BODY), FetchGroup.MINIMAL.with(Profile.MIME_DESCRIPTOR)),
            Arguments.arguments(FetchData.builder().fetch(Item.BODY_STRUCTURE), FetchGroup.MINIMAL.with(Profile.MIME_DESCRIPTOR)),
            Arguments.arguments(FetchData.builder().changedSince(0L), FetchGroup.MINIMAL),
            Arguments.arguments(FetchData.builder().fetch(Item.ENVELOPE), FetchGroup.HEADERS),
            Arguments.arguments(FetchData.builder().fetch(Item.FLAGS), FetchGroup.MINIMAL),
            Arguments.arguments(FetchData.builder().fetch(Item.INTERNAL_DATE), FetchGroup.MINIMAL),
            Arguments.arguments(FetchData.builder().fetch(Item.MODSEQ), FetchGroup.MINIMAL),
            Arguments.arguments(FetchData.builder().fetch(Item.UID), FetchGroup.MINIMAL),
            Arguments.arguments(FetchData.builder().vanished(true), FetchGroup.MINIMAL),
            Arguments.arguments(FetchData.builder().add(BodyFetchElement.createRFC822(), PEEK), FetchGroup.FULL_CONTENT),
            Arguments.arguments(FetchData.builder().add(BodyFetchElement.createRFC822Header(), PEEK), FetchGroup.HEADERS),
            Arguments.arguments(FetchData.builder().add(BodyFetchElement.createRFC822Text(), PEEK), FetchGroup.BODY_CONTENT),
            Arguments.arguments(FetchData.builder().add(new BodyFetchElement(ImapConstants.FETCH_RFC822_HEADER, HEADER, PATH, null, null, null), PEEK),
                FetchGroup.MINIMAL.addPartContent(new MimePath(PATH), Profile.HEADERS)),
            Arguments.arguments(FetchData.builder().add(new BodyFetchElement(ImapConstants.FETCH_RFC822_TEXT, HEADER, PATH, null, null, null), PEEK),
                FetchGroup.MINIMAL.addPartContent(new MimePath(PATH), Profile.BODY_CONTENT)),
            Arguments.arguments(FetchData.builder().add(new BodyFetchElement(ImapConstants.FETCH_RFC822_TEXT, CONTENT, PATH, null, null, null), PEEK),
                FetchGroup.MINIMAL.addPartContent(new MimePath(PATH), Profile.BODY_CONTENT)),
            Arguments.arguments(FetchData.builder().add(new BodyFetchElement(ImapConstants.FETCH_RFC822_TEXT, CONTENT, PATH, null, null, null), PEEK),
                FetchGroup.MINIMAL.addPartContent(new MimePath(PATH), Profile.MIME_CONTENT)),
            Arguments.arguments(FetchData.builder().add(new BodyFetchElement(ImapConstants.FETCH_RFC822_TEXT, MIME, PATH, null, null, null), PEEK),
                FetchGroup.MINIMAL.addPartContent(new MimePath(PATH), Profile.MIME_HEADERS)),
            Arguments.arguments(FetchData.builder().add(new BodyFetchElement(ImapConstants.FETCH_RFC822_TEXT, TEXT, PATH, null, null, null), PEEK),
                FetchGroup.MINIMAL.addPartContent(new MimePath(PATH), Profile.BODY_CONTENT)));
    }

    @ParameterizedTest
    @MethodSource
    void getFetchGroupShouldReturnCorrectValue(FetchData.Builder initial, FetchGroup expected) {
        assertThat(FetchDataConverter.getFetchGroup(initial.build()))
            .isEqualTo(expected);
    }
}