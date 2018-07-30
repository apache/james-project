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

import javax.mail.Flags;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.encode.base.ByteImapResponseWriter;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.mailbox.MessageUid;
import org.junit.Before;
import org.junit.Test;

public class FetchResponseEncoderTest  {
    private ByteImapResponseWriter writer = new ByteImapResponseWriter();
    private ImapResponseComposer composer = new ImapResponseComposerImpl(writer);
   
    private Flags flags;

    private ImapEncoder mockNextEncoder;

    private FetchResponseEncoder encoder;

    @Before
    public void setUp() throws Exception {
        mockNextEncoder = mock(ImapEncoder.class);
        encoder = new FetchResponseEncoder(mockNextEncoder, false);
        flags = new Flags(Flags.Flag.DELETED);
    }

    @Test
    public void testShouldNotAcceptUnknownResponse() {
        assertFalse(encoder.isAcceptable(mock(ImapMessage.class)));
    }

    @Test
    public void testShouldAcceptFetchResponse() {
        assertTrue(encoder.isAcceptable(new FetchResponse(11, null, null, null, null,
                null, null, null, null, null)));
    }

    @Test
    public void testShouldEncodeFlagsResponse() throws Exception {
        FetchResponse message = new FetchResponse(100, flags, null, null, null, null,
                null, null, null, null);
        encoder.doEncode(message, composer, new FakeImapSession());
        assertEquals("* 100 FETCH (FLAGS (\\Deleted))\r\n", writer.getString());


    }

    @Test
    public void testShouldEncodeUidResponse() throws Exception {
        FetchResponse message = new FetchResponse(100, null, MessageUid.of(72), null,
                null, null, null, null, null, null); 
        encoder.doEncode(message, composer, new FakeImapSession());
        assertEquals("* 100 FETCH (UID 72)\r\n", writer.getString());


    }

    @Test
    public void testShouldEncodeAllResponse() throws Exception {
        FetchResponse message = new FetchResponse(100, flags, MessageUid.of(72), null,
                null, null, null, null, null, null);
        encoder.doEncode(message, composer, new FakeImapSession());
        assertEquals("* 100 FETCH (FLAGS (\\Deleted) UID 72)\r\n", writer.getString());
        
    }
}
