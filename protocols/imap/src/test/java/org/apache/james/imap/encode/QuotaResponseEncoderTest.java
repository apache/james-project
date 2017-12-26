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

import org.apache.james.imap.encode.base.ByteImapResponseWriter;
import org.apache.james.imap.encode.base.EndImapEncoder;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.message.response.QuotaResponse;
import org.apache.james.mailbox.store.quota.QuotaImpl;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * QUOTA Response encoder test
 */
public class QuotaResponseEncoderTest {

    @Test
    public void quotaMessageResponseShouldBeWellFormatted() throws Exception {
        QuotaResponse response = new QuotaResponse("MESSAGE", "root", QuotaImpl.quota(231, 1024));
        ByteImapResponseWriter byteImapResponseWriter = new ByteImapResponseWriter();
        ImapResponseComposer composer = new ImapResponseComposerImpl(byteImapResponseWriter, 1024);
        QuotaResponseEncoder encoder = new QuotaResponseEncoder(new EndImapEncoder());
        encoder.encode(response, composer, null);
        String responseString = byteImapResponseWriter.getString();
        assertEquals("* QUOTA root (MESSAGE 231 1024)\r\n", responseString);
    }

    @Test
    public void quotaStorageResponseShouldBeWellFormatted() throws Exception {
        QuotaResponse response = new QuotaResponse("STORAGE", "root", QuotaImpl.quota(231 * 1024, 1024 * 1024));
        ByteImapResponseWriter byteImapResponseWriter = new ByteImapResponseWriter();
        ImapResponseComposer composer = new ImapResponseComposerImpl(byteImapResponseWriter, 1024);
        QuotaResponseEncoder encoder = new QuotaResponseEncoder(new EndImapEncoder());
        encoder.encode(response, composer, null);
        String responseString = byteImapResponseWriter.getString();
        assertEquals("* QUOTA root (STORAGE 231 1024)\r\n", responseString);
    }

}
