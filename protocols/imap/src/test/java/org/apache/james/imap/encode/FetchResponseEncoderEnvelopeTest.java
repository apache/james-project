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

import org.apache.james.imap.encode.base.ByteImapResponseWriter;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.imap.message.response.FetchResponse.Envelope.Address;
import org.junit.Before;
import org.junit.Test;


public class FetchResponseEncoderEnvelopeTest {

    private static final String ADDRESS_ONE_HOST = "HOST";

    private static final String ADDRESS_ONE_MAILBOX = "MAILBOX";

    private static final String ADDRESS_ONE_DOMAIN_LIST = "DOMAIN LIST";

    private static final String ADDRESS_ONE_NAME = "NAME";

    private static final String ADDRESS_TWO_HOST = "2HOST";

    private static final String ADDRESS_TWO_MAILBOX = "2MAILBOX";

    private static final String ADDRESS_TWO_DOMAIN_LIST = "2DOMAIN LIST";

    private static final String ADDRESS_TWO_NAME = "2NAME";

    private static final int MSN = 100;


    private ImapEncoder mockNextEncoder;

    private FetchResponseEncoder encoder;

    private FetchResponse message;

    private FetchResponse.Envelope envelope;

    private Address[] bcc;

    private Address[] cc;

    private String date;

    private Address[] from;

    private String inReplyTo;

    private String messageId;

    private Address[] replyTo;

    private Address[] sender;

    private String subject;

    private Address[] to;
    
    private ByteImapResponseWriter writer = new ByteImapResponseWriter();
    private ImapResponseComposer composer = new ImapResponseComposerImpl(writer);
    
    
    @Before
    public void setUp() throws Exception {
        envelope = mock(FetchResponse.Envelope.class);

        bcc = null;
        cc = null;
        date = null;
        from = null;
        inReplyTo = null;
        messageId = null;
        replyTo = null;
        sender = null;
        subject = null;
        to = null;

        message = new FetchResponse(MSN, null, null, null, null, null, envelope, null, null, null);
        mockNextEncoder = mock(ImapEncoder.class);
        encoder = new FetchResponseEncoder(mockNextEncoder, false);
    }

    private Address[] mockOneAddress() {
        return new Address[]{ mockAddress(ADDRESS_ONE_NAME,
                ADDRESS_ONE_DOMAIN_LIST, ADDRESS_ONE_MAILBOX, ADDRESS_ONE_HOST) };
    }

    private Address[] mockManyAddresses() {
        return new Address[]{
                mockAddress(ADDRESS_ONE_NAME, ADDRESS_ONE_DOMAIN_LIST,
                        ADDRESS_ONE_MAILBOX, ADDRESS_ONE_HOST),
                mockAddress(ADDRESS_TWO_NAME, ADDRESS_TWO_DOMAIN_LIST,
                        ADDRESS_TWO_MAILBOX, ADDRESS_TWO_HOST) };
    }

    private Address mockAddress(final String name, final String domainList,
            final String mailbox, final String host) {
        Address address = mock(Address.class);

        when(address.getPersonalName()).thenReturn(name);
        when(address.getAtDomainList()).thenReturn(domainList);
        when(address.getMailboxName()).thenReturn(mailbox);
        when(address.getHostName()).thenReturn(host);
        return address;
    }

    private void envelopExpects() {
        when(envelope.getBcc()).thenReturn(bcc);
        when(envelope.getCc()).thenReturn(cc);
        when(envelope.getDate()).thenReturn(date);
        when(envelope.getFrom()).thenReturn(from);
        when(envelope.getInReplyTo()).thenReturn(inReplyTo);
        when(envelope.getMessageId()).thenReturn(messageId);
        when(envelope.getReplyTo()).thenReturn(replyTo);
        when(envelope.getSender()).thenReturn(sender);
        when(envelope.getSubject()).thenReturn(subject);
        when(envelope.getTo()).thenReturn(to);
    }

    @Test
    public void testShouldNilAllNullProperties() throws Exception {
        envelopExpects();
        encoder.doEncode(message, composer, new FakeImapSession());
        
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL NIL NIL NIL NIL NIL NIL NIL NIL NIL))\r\n");
    }

    @Test
    public void testShouldComposeDate() throws Exception {
        date = "a date";
        envelopExpects();
        
        
        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (\"a date\" NIL NIL NIL NIL NIL NIL NIL NIL NIL))\r\n");

    }
    
    @Test
    public void testShouldComposeSubject() throws Exception {
        subject = "some subject";
        envelopExpects();
        
        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL \"some subject\" NIL NIL NIL NIL NIL NIL NIL NIL))\r\n");

    }

    @Test
    public void testShouldComposeInReplyTo() throws Exception {
        inReplyTo = "some reply to";
        envelopExpects();
       
        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL NIL NIL NIL NIL NIL NIL NIL \"some reply to\" NIL))\r\n");
    }

    @Test
    public void testShouldComposeMessageId() throws Exception {
        messageId = "some message id";
        envelopExpects();
        
        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL NIL NIL NIL NIL NIL NIL NIL NIL \"some message id\"))\r\n");

    }

    @Test
    public void testShouldComposeOneFromAddress() throws Exception {
        from = mockOneAddress();
        envelopExpects();
        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL NIL ((\"NAME\" \"DOMAIN LIST\" \"MAILBOX\" \"HOST\")) NIL NIL NIL NIL NIL NIL NIL))\r\n");

    }

    @Test
    public void testShouldComposeManyFromAddress() throws Exception {
        from = mockManyAddresses();
        envelopExpects();
        
        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL NIL ((\"NAME\" \"DOMAIN LIST\" \"MAILBOX\" \"HOST\")(\"2NAME\" \"2DOMAIN LIST\" \"2MAILBOX\" \"2HOST\")) NIL NIL NIL NIL NIL NIL NIL))\r\n");

    }

    @Test
    public void testShouldComposeOneSenderAddress() throws Exception {
        sender = mockOneAddress();
        envelopExpects();
     
        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL NIL NIL ((\"NAME\" \"DOMAIN LIST\" \"MAILBOX\" \"HOST\")) NIL NIL NIL NIL NIL NIL))\r\n");

    }

    @Test
    public void testShouldComposeManySenderAddress() throws Exception {
        sender = mockManyAddresses();
        envelopExpects();
     
        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL NIL NIL ((\"NAME\" \"DOMAIN LIST\" \"MAILBOX\" \"HOST\")(\"2NAME\" \"2DOMAIN LIST\" \"2MAILBOX\" \"2HOST\")) NIL NIL NIL NIL NIL NIL))\r\n");

    }
    

    @Test
    public void testShouldComposeOneReplyToAddress() throws Exception {
        replyTo = mockOneAddress();
        envelopExpects();
       
        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL NIL NIL NIL ((\"NAME\" \"DOMAIN LIST\" \"MAILBOX\" \"HOST\")) NIL NIL NIL NIL NIL))\r\n");

    }

    @Test
    public void testShouldComposeManyReplyToAddress() throws Exception {
        replyTo = mockManyAddresses();
        envelopExpects();
       
        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL NIL NIL NIL ((\"NAME\" \"DOMAIN LIST\" \"MAILBOX\" \"HOST\")(\"2NAME\" \"2DOMAIN LIST\" \"2MAILBOX\" \"2HOST\")) NIL NIL NIL NIL NIL))\r\n");

    }

    @Test
    public void testShouldComposeOneToAddress() throws Exception {
        to = mockOneAddress();
        envelopExpects();
       
        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL NIL NIL NIL NIL ((\"NAME\" \"DOMAIN LIST\" \"MAILBOX\" \"HOST\")) NIL NIL NIL NIL))\r\n");

    }

    @Test
    public void testShouldComposeManyToAddress() throws Exception {
        to = mockManyAddresses();
        envelopExpects();
       
        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL NIL NIL NIL NIL ((\"NAME\" \"DOMAIN LIST\" \"MAILBOX\" \"HOST\")(\"2NAME\" \"2DOMAIN LIST\" \"2MAILBOX\" \"2HOST\")) NIL NIL NIL NIL))\r\n");

    }

    @Test
    public void testShouldComposeOneCcAddress() throws Exception {
        cc = mockOneAddress();
        envelopExpects();

        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL NIL NIL NIL NIL NIL ((\"NAME\" \"DOMAIN LIST\" \"MAILBOX\" \"HOST\")) NIL NIL NIL))\r\n");

    }

    @Test
    public void testShouldComposeManyCcAddress() throws Exception {
        cc = mockManyAddresses();
        envelopExpects();
       
        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL NIL NIL NIL NIL NIL ((\"NAME\" \"DOMAIN LIST\" \"MAILBOX\" \"HOST\")(\"2NAME\" \"2DOMAIN LIST\" \"2MAILBOX\" \"2HOST\")) NIL NIL NIL))\r\n");

    }
    
    @Test
    public void testShouldComposeOneBccAddress() throws Exception {
        bcc = mockOneAddress();
        envelopExpects();
       
        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL NIL NIL NIL NIL NIL NIL ((\"NAME\" \"DOMAIN LIST\" \"MAILBOX\" \"HOST\")) NIL NIL))\r\n");

    }

    @Test
    public void testShouldComposeManyBccAddress() throws Exception {
        bcc = mockManyAddresses();
        envelopExpects();
       
        encoder.doEncode(message, composer, new FakeImapSession());
        assertThat(writer.getString()).isEqualTo("* 100 FETCH (ENVELOPE (NIL NIL NIL NIL NIL NIL NIL ((\"NAME\" \"DOMAIN LIST\" \"MAILBOX\" \"HOST\")(\"2NAME\" \"2DOMAIN LIST\" \"2MAILBOX\" \"2HOST\")) NIL NIL))\r\n");

    }
}
