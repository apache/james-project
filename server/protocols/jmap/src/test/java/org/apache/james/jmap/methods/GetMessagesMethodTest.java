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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.apache.james.jmap.model.MessageContentExtractor;
import org.apache.james.jmap.model.MessageFactory;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.MessagePreviewGenerator;
import org.apache.james.jmap.model.MessageProperties.MessageProperty;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.assertj.core.api.Condition;
import org.assertj.core.data.MapEntry;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.jayway.jsonpath.JsonPath;

public class GetMessagesMethodTest {

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

        @Override
        public boolean isSameUser(String username) {
            return this.username.equalsIgnoreCase(username);
        }
    }
    
    private static final User ROBERT = new User("robert", "secret");

    private MailboxManager mailboxManager;
    private GetMessagesMethod testee;

    private MailboxSession session;
    private MailboxPath inboxPath;
    private ClientId clientId;

    @Before
    public void setup() throws Exception {
        clientId = ClientId.of("#0");
        MessagePreviewGenerator messagePreview = mock(MessagePreviewGenerator.class);
        when(messagePreview.forHTMLBody(any())).thenReturn("html preview");
        when(messagePreview.forTextBody(any())).thenReturn("text preview");
        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();
        MessageFactory messageFactory = new MessageFactory(messagePreview, messageContentExtractor);
        InMemoryIntegrationResources inMemoryIntegrationResources = new InMemoryIntegrationResources();
        GroupMembershipResolver groupMembershipResolver = inMemoryIntegrationResources.createGroupMembershipResolver();
        mailboxManager = inMemoryIntegrationResources.createMailboxManager(groupMembershipResolver);

        session = new MockMailboxSession(ROBERT.username);
        inboxPath = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inboxPath, session);
        testee = new GetMessagesMethod(mailboxManager, messageFactory);
    }
    
    @Test
    public void processShouldThrowWhenNullRequest() {
        GetMessagesRequest request = null;
        assertThatThrownBy(() -> testee.process(request, mock(ClientId.class), mock(MailboxSession.class))).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenNullSession() {
        MailboxSession mailboxSession = null;
        assertThatThrownBy(() -> testee.process(mock(GetMessagesRequest.class), mock(ClientId.class), mailboxSession)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenNullClientId() {
        ClientId clientId = null;
        assertThatThrownBy(() -> testee.process(mock(GetMessagesRequest.class), clientId, mock(MailboxSession.class))).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenRequestHasAccountId() {
        assertThatThrownBy(() -> testee.process(
                GetMessagesRequest.builder().accountId("abc").build(), mock(ClientId.class), mock(MailboxSession.class))).isInstanceOf(NotImplementedException.class);
    }
    
    @Test
    public void processShouldFetchMessages() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        Date now = new Date();
        ByteArrayInputStream message1Content = new ByteArrayInputStream("Subject: message 1 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        ComposedMessageId message1 = inbox.appendMessage(message1Content, now, session, false, null);
        ByteArrayInputStream message2Content = new ByteArrayInputStream("Subject: message 2 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        ComposedMessageId message2 = inbox.appendMessage(message2Content, now, session, false, null);
        ByteArrayInputStream message3Content = new ByteArrayInputStream("Great-Header: message 3 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        ComposedMessageId message3 = inbox.appendMessage(message3Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, message1.getUid()),
                          new MessageId(ROBERT, inboxPath, message2.getUid()),
                          new MessageId(ROBERT, inboxPath, message3.getUid())))
                .build();

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());
        
        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .extracting(message -> message.getId().getUid(), Message::getSubject, Message::getTextBody)
            .containsOnly(
                    Tuple.tuple(message1.getUid(), "message 1 subject", Optional.of("my message")), 
                    Tuple.tuple(message2.getUid(), "message 2 subject", Optional.of("my message")),
                    Tuple.tuple(message3.getUid(), "", Optional.of("my message")));
    }
    
    @Test
    public void processShouldFetchHtmlMessage() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        Date now = new Date();
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("Content-Type: text/html\r\n"
                + "Subject: message 1 subject\r\n"
                + "\r\n"
                + "my <b>HTML</b> message").getBytes(Charsets.UTF_8));
        ComposedMessageId messageId = inbox.appendMessage(messageContent, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, messageId.getUid())))
                .build();

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());
        
        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .extracting(message -> message.getId().getUid(), Message::getTextBody, Message::getHtmlBody)
            .containsOnly(Tuple.tuple(messageId.getUid(), Optional.empty(), Optional.of("my <b>HTML</b> message")));
    }

    @Test
    public void processShouldReturnOnlyMandatoryPropertiesOnEmptyPropertyList() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        Date now = new Date();
        ByteArrayInputStream message1Content = new ByteArrayInputStream("Subject: message 1 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        ComposedMessageId message1 = inbox.appendMessage(message1Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, message1.getUid())))
                .properties(ImmutableList.of())
                .build();

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
        ComposedMessageId message1 = inbox.appendMessage(message1Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, message1.getUid())))
                .build();

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
        ComposedMessageId message1 = inbox.appendMessage(message1Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, message1.getUid())))
                .properties(ImmutableList.of(MessageProperty.subject.asFieldName()))
                .build();

        Set<MessageProperty> expected = Sets.newHashSet(MessageProperty.id, MessageProperty.subject);

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
        ComposedMessageId message1 = inbox.appendMessage(message1Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, message1.getUid())))
                .properties(ImmutableList.of(MessageProperty.body.asFieldName()))
                .build();

        Set<MessageProperty> expected = Sets.newHashSet(MessageProperty.id, MessageProperty.textBody);

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
        ComposedMessageId message1 = inbox.appendMessage(message1Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, message1.getUid())))
                .properties(ImmutableList.of("headers.from", "headers.heADER2"))
                .build();

        Set<MessageProperty> expected = Sets.newHashSet(MessageProperty.id, MessageProperty.headers);

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
        ComposedMessageId message1 = inbox.appendMessage(message1Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(new MessageId(ROBERT, inboxPath, message1.getUid())))
                .properties(ImmutableList.of("headers.from", "headers.heADER2"))
                .build();

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result)
            .hasSize(1)
            .extracting(JmapResponse::getFilterProvider)
            .are(new Condition<>(Optional::isPresent, "present"));
        SimpleFilterProvider actualFilterProvider = result.get(0).getFilterProvider().get();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setFilterProvider(actualFilterProvider.setDefaultFilter(SimpleBeanPropertyFilter.serializeAll()));
        String response = objectMapper.writer().writeValueAsString(result.get(0));
        assertThat(JsonPath.parse(response).<Map<String, String>>read("$.response.list[0].headers")).containsOnly(MapEntry.entry("From", "user@domain.tld"), MapEntry.entry("HEADer2", "Header2Content"));
    }
}
