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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.encode.base.ByteImapResponseWriter;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.message.response.LSubResponse;
import org.apache.james.imap.message.response.SearchResponse;
import org.junit.Before;
import org.junit.Test;

public class SearchResponseEncoderTest {

    private static final long[] IDS = { 1, 4, 9, 16 };

    private SearchResponse response;

    private SearchResponseEncoder encoder;

    private ImapEncoder mockNextEncoder;

    private ByteImapResponseWriter writer = new ByteImapResponseWriter();
    private ImapResponseComposer composer = new ImapResponseComposerImpl(writer);

    @Before
    public void setUp() throws Exception {
        mockNextEncoder = mock(ImapEncoder.class);
        response = new SearchResponse(IDS, null);
        encoder = new SearchResponseEncoder(mockNextEncoder);
    }

    @Test
    public void testIsAcceptable() {
        assertTrue(encoder.isAcceptable(response));
        assertFalse(encoder.isAcceptable(new LSubResponse("name", true, '.')));
        assertFalse(encoder.isAcceptable(mock(ImapMessage.class)));
        assertFalse(encoder.isAcceptable(null));
    }

    @Test
    public void testEncode() throws Exception {
        encoder.encode(response, composer, new FakeImapSession());
        assertEquals("* SEARCH 1 4 9 16\r\n", writer.getString());
    }
}
