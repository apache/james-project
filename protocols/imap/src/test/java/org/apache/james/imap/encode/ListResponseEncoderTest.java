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
import static org.junit.Assert.assertFalse;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.encode.base.ByteImapResponseWriter;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.message.response.LSubResponse;
import org.apache.james.imap.message.response.ListResponse;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;

public class ListResponseEncoderTest {

    private ListResponseEncoder encoder;

    private ByteImapResponseWriter writer = new ByteImapResponseWriter();
    private ImapResponseComposer composer = new ImapResponseComposerImpl(writer);

    private Mockery context = new JUnit4Mockery();
    
    @Before
    public void setUp() throws Exception {
        encoder = new ListResponseEncoder(context.mock(ImapEncoder.class));
    }

    @Test
    public void encoderShouldAcceptListResponse() {
        assertThat(encoder.isAcceptable(new ListResponse(MailboxMetaData.Children.HAS_CHILDREN, MailboxMetaData.Selectability.NONE, "name", '.')))
        .isTrue();
    }

    @Test
    public void encoderShouldNotAcceptLsubResponse() {
        assertThat(encoder.isAcceptable(new LSubResponse("name", true, '.'))).isFalse();
        assertFalse(encoder.isAcceptable(context.mock(ImapMessage.class)));
        assertFalse(encoder.isAcceptable(null));
    }

    @Test
    public void encoderShouldNotAcceptImapMessage() {
        assertThat(encoder.isAcceptable(context.mock(ImapMessage.class))).isFalse();
    }

    @Test
    public void encoderShouldNotAcceptNull() {
        assertThat(encoder.isAcceptable(null)).isFalse();
    }

    @Test
    public void encoderShouldIncludeListCommand() throws Exception {
        encoder.encode(new ListResponse(MailboxMetaData.Children.HAS_CHILDREN, MailboxMetaData.Selectability.NONE, "name", '.'), composer, new FakeImapSession());
        assertThat(writer.getString()).startsWith("* LIST");
    }
}
