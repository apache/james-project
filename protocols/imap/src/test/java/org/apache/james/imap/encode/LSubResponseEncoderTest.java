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

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.encode.base.ByteImapResponseWriter;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.message.response.LSubResponse;
import org.apache.james.imap.message.response.ListResponse;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.junit.Before;
import org.junit.Test;

public class LSubResponseEncoderTest  {
    private LSubResponseEncoder encoder;

    private ByteImapResponseWriter writer = new ByteImapResponseWriter();
    private ImapResponseComposer composer = new ImapResponseComposerImpl(writer);

    @Before
    public void setUp() throws Exception {
        encoder = new LSubResponseEncoder(mock(ImapEncoder.class));
    }

    @Test
    public void encoderShouldNotAcceptListResponse() {
        assertThat(encoder.isAcceptable(new ListResponse(MailboxMetaData.Children.HAS_CHILDREN, MailboxMetaData.Selectability.NOSELECT, "name", '.')))
            .isFalse();
    }

    @Test
    public void encoderShouldAcceptLsubResponse() {
        assertThat(encoder.isAcceptable(new LSubResponse("name", true, '.'))).isTrue();
    }

    @Test
    public void encoderShouldNotAcceptImapMessage() {
        assertThat(encoder.isAcceptable(mock(ImapMessage.class))).isFalse();
    }

    @Test
    public void encoderShouldNotAcceptNull() {
        assertThat(encoder.isAcceptable(null)).isFalse();
    }

    @Test
    public void encoderShouldIncludeLSUBCommand() throws Exception {
        encoder.encode(new LSubResponse("name", true, '.'), composer, new FakeImapSession());
        assertThat(writer.getString()).startsWith("* LSUB");
    }

}
