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

package org.apache.james.imap.encode.main;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.stream.Stream;

import org.apache.james.imap.encode.ImapResponseComposer;
import org.apache.james.imap.encode.RecentResponseEncoder;
import org.apache.james.imap.encode.base.EndImapEncoder;
import org.apache.james.imap.message.response.RecentResponse;
import org.junit.jupiter.api.Test;

class DefaultImapEncoderFactoryTest {
    @Test
    void defaultImapEncoderConstructorShouldThrowOnDuplicatedProcessor() {
        assertThatThrownBy(() -> new DefaultImapEncoderFactory.DefaultImapEncoder(
            Stream.of(
                new RecentResponseEncoder(),
                new RecentResponseEncoder()),
            new EndImapEncoder()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodeShouldTagNoWhenUnknownMessage() throws Exception {
        DefaultImapEncoderFactory.DefaultImapEncoder defaultImapEncoder = new DefaultImapEncoderFactory.DefaultImapEncoder(
            Stream.empty(),
            new EndImapEncoder());

        ImapResponseComposer composer = mock(ImapResponseComposer.class);
        defaultImapEncoder.encode(new RecentResponse(18), composer);

        verify(composer).untaggedNoResponse("Unknown message in pipeline", null);
    }
}