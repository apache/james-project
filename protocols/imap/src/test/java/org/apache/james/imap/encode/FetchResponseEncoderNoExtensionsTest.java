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

package org.apache.james.imap.encode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.mail.Flags;

import org.apache.james.imap.encode.base.ByteImapResponseWriter;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.mailbox.MessageSequenceNumber;
import org.apache.james.mailbox.MessageUid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FetchResponseEncoderNoExtensionsTest {
    private static final MessageSequenceNumber MSN = MessageSequenceNumber.of(100);
    private ByteImapResponseWriter writer = new ByteImapResponseWriter();
    private ImapResponseComposer composer = new ImapResponseComposerImpl(writer);
    private Flags flags;

    private FetchResponse.Structure stubStructure;
    private ImapEncoder mockNextEncoder;
    private FetchResponseEncoder encoder;

    @BeforeEach
    void setUp() throws Exception {
        encoder = new FetchResponseEncoder(true);
        flags = new Flags(Flags.Flag.DELETED);
        stubStructure = mock(FetchResponse.Structure.class);
    }

    @Test
    void testShouldEncodeFlagsResponse() throws Exception {
        FetchResponse message = new FetchResponse(MSN, flags, null, null, null, null, null,
                null, null, null, null, null, null);
        encoder.encode(message, composer);
        composer.flush();
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (FLAGS (\\Deleted))\r\n");
    }

    @Test
    void testShouldEncodeUidResponse() throws Exception {
        FetchResponse message = new FetchResponse(MSN, null, MessageUid.of(72), null, null,
                null, null, null, null, null, null, null, null);
        encoder.encode(message, composer);
        composer.flush();
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (UID 72)\r\n");

    }

    @Test
    void testShouldEncodeAllResponse() throws Exception {
        FetchResponse message = new FetchResponse(MSN, flags, MessageUid.of(72), null, null,
                null, null, null, null, null, null, null, null);
        encoder.encode(message, composer);
        composer.flush();
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (FLAGS (\\Deleted) UID 72)\r\n");

    }

    @Test
    void testShouldNotAddExtensionsWithEncodingBodyStructure() throws Exception {
        FetchResponse message = new FetchResponse(MSN, flags, MessageUid.of(72), null, null,
                null, null, null, null, stubStructure, null, null, null);
        final Map<String, String> parameters = new HashMap<>();
        parameters.put("CHARSET", "US-ASCII");
        final List<String> parameterList = new ArrayList<>();
        parameterList.add("CHARSET");
        parameterList.add("US-ASCII");

        when(stubStructure.getMediaType()).thenReturn("TEXT");
        when(stubStructure.getSubType()).thenReturn("HTML");
        when(stubStructure.getOctets()).thenReturn(2279L);
        when(stubStructure.getLines()).thenReturn(48L);
        when(stubStructure.getParameters()).thenReturn(parameterList);
        when(stubStructure.getEncoding()).thenReturn("7BIT");
        when(stubStructure.getId()).thenReturn("");
        when(stubStructure.getDescription()).thenReturn("");

        encoder.encode(message, composer);
        composer.flush();
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (FLAGS (\\Deleted) BODYSTRUCTURE (\"TEXT\" \"HTML\" (\"CHARSET\" \"US-ASCII\") \"\" \"\" \"7BIT\" 2279 48) UID 72)\r\n");

    }
}
