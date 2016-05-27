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
package org.apache.james.jmap.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.GetMessagesRequest;
import org.apache.james.jmap.model.GetMessagesResponse;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.MessageProperties.MessageProperty;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MockAuthenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.assertj.core.api.Condition;
import org.assertj.core.data.MapEntry;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.jayway.jsonpath.JsonPath;

public class GetMessagesMethodTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetMessagesMethodTest.class);
    
    private static class User implements org.apache.james.mailbox.MailboxSession.User {
        final String username;
        final String password;

        public User(String username, String password) {
            this.username = username;
            this.password = password;
        }
        
        @Override
        public String getUserName() {
            return username;
        }

        @Override
        public String getPassword() {
            return password;
        }
        
        @Override
        public List<Locale> getLocalePreferences() {
            return ImmutableList.of();
        }
    }
    
    private static final User ROBERT = new User("robert", "secret");

    private StoreMailboxManager mailboxManager;
    private InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory;

    private MailboxSession session;
    private MailboxPath inboxPath;
    private ClientId clientId;

    @Before
    public void setup() throws MailboxException {
        clientId = ClientId.of("#0");
        mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
        MockAuthenticator authenticator = new MockAuthenticator();
        authenticator.addUser(ROBERT.username, ROBERT.password);
        UnionMailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        SimpleGroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();
        mailboxManager = new StoreMailboxManager(mailboxSessionMapperFactory, authenticator, aclResolver, groupMembershipResolver, messageParser);
        mailboxManager.init();
        

        session = mailboxManager.login(ROBERT.username, ROBERT.password, LOGGER);
        inboxPath = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inboxPath, session);
    }
    
    @Test
    public void processShouldThrowWhenNullRequest() {
        GetMessagesMethod testee = new GetMessagesMethod(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        GetMessagesRequest request = null;
        assertThatThrownBy(() -> testee.process(request, mock(ClientId.class), mock(MailboxSession.class))).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenNullSession() {
        GetMessagesMethod testee = new GetMessagesMethod(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        MailboxSession mailboxSession = null;
        assertThatThrownBy(() -> testee.process(mock(GetMessagesRequest.class), mock(ClientId.class), mailboxSession)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenNullClientId() {
        GetMessagesMethod testee = new GetMessagesMethod(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        ClientId clientId = null;
        assertThatThrownBy(() -> testee.process(mock(GetMessagesRequest.class), clientId, mock(MailboxSession.class))).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenRequestHasAccountId() {
        GetMessagesMethod testee = new GetMessagesMethod(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        assertThatThrownBy(() -> testee.process(
                GetMessagesRequest.builder().accountId("abc").build(), mock(ClientId.class), mock(MailboxSession.class))).isInstanceOf(NotImplementedException.class);
    }
    
    @Test
    public void processShouldFetchMessages() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        Date now = new Date();
        ByteArrayInputStream message1Content = new ByteArrayInputStream("Subject: message 1 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        long message1Uid = inbox.appendMessage(message1Content, now, session, false, null);
        ByteArrayInputStream message2Content = new ByteArrayInputStream("Subject: message 2 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        long message2Uid = inbox.appendMessage(message2Content, now, session, false, null);
        ByteArrayInputStream message3Content = new ByteArrayInputStream("Great-Header: message 3 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        long message3Uid = inbox.appendMessage(message3Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, message1Uid),
                          new MessageId(ROBERT, inboxPath, message2Uid),
                          new MessageId(ROBERT, inboxPath, message3Uid)))
                .build();

        GetMessagesMethod testee = new GetMessagesMethod(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());
        
        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .extracting(message -> message.getId().getUid(), Message::getSubject, Message::getTextBody)
            .containsOnly(
                    Tuple.tuple(message1Uid, "message 1 subject", Optional.of("my message")), 
                    Tuple.tuple(message2Uid, "message 2 subject", Optional.of("my message")),
                    Tuple.tuple(message3Uid, "(No subject)", Optional.of("my message")));
    }
    
    @Test
    public void processShouldFetchHtmlMessage() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        Date now = new Date();
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("Content-Type: text/html\r\n"
                + "Subject: message 1 subject\r\n"
                + "\r\n"
                + "my <b>HTML</b> message").getBytes(Charsets.UTF_8));
        long messageUid = inbox.appendMessage(messageContent, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, messageUid)))
                .build();

        GetMessagesMethod testee = new GetMessagesMethod(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());
        
        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .extracting(message -> message.getId().getUid(), Message::getTextBody, Message::getHtmlBody)
            .containsOnly(Tuple.tuple(messageUid, Optional.empty(), Optional.of("my <b>HTML</b> message")));
    }

    @Test
    public void processShouldReturnOnlyMandatoryPropertiesOnEmptyPropertyList() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        Date now = new Date();
        ByteArrayInputStream message1Content = new ByteArrayInputStream("Subject: message 1 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        long message1Uid = inbox.appendMessage(message1Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, message1Uid)))
                .properties(ImmutableList.of())
                .build();

        GetMessagesMethod testee = new GetMessagesMethod(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getProperties)
            .flatExtracting(Optional::get)
            .asList()
            .containsOnly(MessageProperty.id);
    }

    @Test
    public void processShouldReturnAllPropertiesWhenNoPropertyGiven() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        Date now = new Date();
        ByteArrayInputStream message1Content = new ByteArrayInputStream("Subject: message 1 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        long message1Uid = inbox.appendMessage(message1Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, message1Uid)))
                .build();

        GetMessagesMethod testee = new GetMessagesMethod(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        Stream<JmapResponse> result = testee.process(request, clientId, session);

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getProperties)
            .flatExtracting(Optional::get)
            .asList()
            .containsOnlyElementsOf(MessageProperty.allOutputProperties());
    }

    @Test
    public void processShouldAddMandatoryPropertiesWhenNotInPropertyList() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        Date now = new Date();
        ByteArrayInputStream message1Content = new ByteArrayInputStream("Subject: message 1 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        long message1Uid = inbox.appendMessage(message1Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, message1Uid)))
                .properties(ImmutableList.of(MessageProperty.subject.asFieldName()))
                .build();

        Set<MessageProperty> expected = Sets.newHashSet(MessageProperty.id, MessageProperty.subject);

        GetMessagesMethod testee = new GetMessagesMethod(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getProperties)
            .flatExtracting(Optional::get)
            .asList()
            .containsOnlyElementsOf(expected);
    }
    
    @Test
    public void processShouldReturnTextBodyWhenBodyInPropertyListAndEmptyHtmlBody() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        Date now = new Date();
        ByteArrayInputStream message1Content = new ByteArrayInputStream("Subject: message 1 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        long message1Uid = inbox.appendMessage(message1Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, message1Uid)))
                .properties(ImmutableList.of(MessageProperty.body.asFieldName()))
                .build();

        Set<MessageProperty> expected = Sets.newHashSet(MessageProperty.id, MessageProperty.textBody);

        GetMessagesMethod testee = new GetMessagesMethod(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getProperties)
            .flatExtracting(Optional::get)
            .asList()
            .containsOnlyElementsOf(expected);
    }
    
    @Test
    public void processShouldReturnHeadersFieldWhenSpecificHeadersRequestedInPropertyList() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        Date now = new Date();
        ByteArrayInputStream message1Content = new ByteArrayInputStream(("From: user@domain.tld\r\n"
                + "header1: Header1Content\r\n"
                + "HEADer2: Header2Content\r\n"
                + "Subject: message 1 subject\r\n\r\nmy message").getBytes(Charsets.UTF_8));
        long message1Uid = inbox.appendMessage(message1Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, message1Uid)))
                .properties(ImmutableList.of("headers.from", "headers.heADER2"))
                .build();

        Set<MessageProperty> expected = Sets.newHashSet(MessageProperty.id, MessageProperty.headers);

        GetMessagesMethod testee = new GetMessagesMethod(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result)
            .hasSize(1)
            .extracting(JmapResponse::getProperties)
            .flatExtracting(Optional::get)
            .asList()
            .containsOnlyElementsOf(expected);
    }
    
    @Test
    public void processShouldReturnPropertyFilterWhenFilteringHeadersRequested() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        Date now = new Date();
        ByteArrayInputStream message1Content = new ByteArrayInputStream(("From: user@domain.tld\r\n"
                + "header1: Header1Content\r\n"
                + "HEADer2: Header2Content\r\n"
                + "Subject: message 1 subject\r\n\r\nmy message").getBytes(Charsets.UTF_8));
        long message1Uid = inbox.appendMessage(message1Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, message1Uid)))
                .properties(ImmutableList.of("headers.from", "headers.heADER2"))
                .build();

        GetMessagesMethod testee = new GetMessagesMethod(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result)
            .hasSize(1)
            .extracting(JmapResponse::getFilterProvider)
            .are(new Condition<>(Optional::isPresent, "present"));
        SimpleFilterProvider actualFilterProvider = result.get(0).getFilterProvider().get();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setFilterProvider(actualFilterProvider.setDefaultFilter(SimpleBeanPropertyFilter.serializeAll()));
        String response = objectMapper.writer().writeValueAsString(result.get(0));
        assertThat(JsonPath.parse(response).<Map<String, String>>read("$.response.list[0].headers")).containsOnly(MapEntry.entry("from", "user@domain.tld"), MapEntry.entry("header2", "Header2Content"));
    }
}
