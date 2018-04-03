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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.GetMessagesRequest;
import org.apache.james.jmap.model.GetMessagesResponse;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageFactory;
import org.apache.james.jmap.model.MessagePreviewGenerator;
import org.apache.james.jmap.model.MessageProperties.MessageProperty;
import org.apache.james.jmap.utils.HtmlTextExtractor;
import org.apache.james.jmap.utils.JsoupHtmlTextExtractor;
import org.apache.james.mailbox.BlobManager;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.BlobId;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.util.mime.MessageContentExtractor;
import org.assertj.core.api.Condition;
import org.assertj.core.data.MapEntry;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.jayway.jsonpath.JsonPath;

public class GetMessagesMethodTest {
    private static final String FORWARDED = "forwarded";
    private MessageIdManager messageIdManager;

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

    private StoreMailboxManager mailboxManager;
    private GetMessagesMethod testee;

    private MailboxSession session;
    private MailboxPath inboxPath;
    private MailboxPath customMailboxPath;
    private ClientId clientId;
    
    @Before
    public void setup() throws Exception {
        clientId = ClientId.of("#0");
        HtmlTextExtractor htmlTextExtractor = new JsoupHtmlTextExtractor();
        MessagePreviewGenerator messagePreview = new MessagePreviewGenerator();
        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();
        BlobManager blobManager = mock(BlobManager.class);
        when(blobManager.toBlobId(any(MessageId.class))).thenReturn(BlobId.fromString("fake"));
        MessageFactory messageFactory = new MessageFactory(blobManager, messagePreview, messageContentExtractor, htmlTextExtractor);
        InMemoryIntegrationResources inMemoryIntegrationResources = new InMemoryIntegrationResources();
        GroupMembershipResolver groupMembershipResolver = inMemoryIntegrationResources.createGroupMembershipResolver();
        mailboxManager = inMemoryIntegrationResources.createMailboxManager(groupMembershipResolver);

        session = new MockMailboxSession(ROBERT.username);
        inboxPath = MailboxPath.inbox(session);
        customMailboxPath = new MailboxPath(inboxPath, "custom");
        mailboxManager.createMailbox(inboxPath, session);
        mailboxManager.createMailbox(customMailboxPath, session);
        messageIdManager = inMemoryIntegrationResources.createMessageIdManager(mailboxManager);
        testee = new GetMessagesMethod(messageFactory, messageIdManager, new DefaultMetricFactory());
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
        String message1Content = "Subject: message 1 subject\r\n\r\nmy message";
        ComposedMessageId message1 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(message1Content),
            session);
        String message2Content = "Subject: message 2 subject\r\n\r\nmy message";
        ComposedMessageId message2 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(message2Content),
            session);
        String message3Content = "Great-Header: message 3 subject\r\n\r\nmy message";
        ComposedMessageId message3 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(message3Content),
            session);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(message1.getMessageId(),
                        message2.getMessageId(),
                        message3.getMessageId()))
                .build();

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());
        
        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .extracting(Message::getId, Message::getSubject, Message::getTextBody)
            .containsOnly(
                Tuple.tuple(message1.getMessageId(), "message 1 subject", Optional.of("my message")),
                Tuple.tuple(message2.getMessageId(), "message 2 subject", Optional.of("my message")),
                Tuple.tuple(message3.getMessageId(), "", Optional.of("my message")));
    }
    
    @Test
    public void processShouldFetchHtmlMessage() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        String messageContent ="Content-Type: text/html\r\n"
                + "Subject: message 1 subject\r\n"
                + "\r\n"
                + "my <b>HTML</b> message";
        ComposedMessageId message = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(messageContent),
            session);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(message.getMessageId()))
                .build();

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());
        
        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .extracting(Message::getId, Message::getHtmlBody)
            .containsOnly(Tuple.tuple(message.getMessageId(), Optional.of("my <b>HTML</b> message")));
    }

    @Test
    public void processShouldReturnOnlyMandatoryPropertiesOnEmptyPropertyList() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        String message1Content = "Subject: message 1 subject\r\n\r\nmy message";
        ComposedMessageId message1 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(message1Content),
            session);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(message1.getMessageId()))
                .properties(ImmutableList.of())
                .build();

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProperties())
            .isEqualTo(Optional.of(ImmutableSet.of(MessageProperty.id)));
    }

    @Test
    public void processShouldReturnAllPropertiesWhenNoPropertyGiven() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        String message1Content = "Subject: message 1 subject\r\n\r\nmy message";
        ComposedMessageId message1 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(message1Content),
            session);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(message1.getMessageId()))
                .build();

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProperties())
            .isEqualTo(Optional.of(MessageProperty.allOutputProperties()));
    }

    @Test
    public void processShouldAddMandatoryPropertiesWhenNotInPropertyList() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        String message1Content = "Subject: message 1 subject\r\n\r\nmy message";
        ComposedMessageId message1 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(message1Content),
            session);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(message1.getMessageId()))
                .properties(ImmutableList.of(MessageProperty.subject.asFieldName()))
                .build();

        Set<MessageProperty> expected = Sets.newHashSet(MessageProperty.id, MessageProperty.subject);

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProperties())
            .isEqualTo(Optional.of(expected));
    }
    
    @Test
    public void processShouldReturnTextBodyWhenBodyInPropertyListAndEmptyHtmlBody() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        String message1Content = "Subject: message 1 subject\r\n\r\nmy message";
        ComposedMessageId message1 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(message1Content),
            session);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(message1.getMessageId()))
                .properties(ImmutableList.of(MessageProperty.body.asFieldName()))
                .build();

        Set<MessageProperty> expected = Sets.newHashSet(MessageProperty.id, MessageProperty.textBody);

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProperties())
            .isEqualTo(Optional.of(expected));
    }

    @Test
    public void processShouldReturnTextBodyWhenEmptyTextBodyAndNotEmptyHtmlBody() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        String messageContent = "Content-Type: text/html\r\n"
            + "Subject: message 1 subject\r\n"
            + "\r\n"
            + "my <b>HTML</b> message";
        ComposedMessageId message = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(messageContent),
            session);

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message.getMessageId()))
            .build();

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .extracting(Message::getId, Message::getTextBody, Message::getHtmlBody)
            .containsOnly(Tuple.tuple(message.getMessageId(), Optional.of("my HTML message"), Optional.of("my <b>HTML</b> message")));
    }

    @Test
    public void processShouldReturnEmptyTextBodyAndHtmlBodyWhenThoseAreEmpty() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        String messageContent = "Content-Type: text/html\r\n"
            + "Subject: message 1 subject\r\n"
            + "\r\n";
        ComposedMessageId message = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(messageContent),
            session);

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message.getMessageId()))
            .build();

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .extracting(Message::getId, Message::getTextBody, Message::getHtmlBody)
            .containsOnly(Tuple.tuple(message.getMessageId(), Optional.empty(), Optional.of("")));
    }

    @Test
    public void processShouldNotOverrideTextBodyWhenItIsThere() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        String messageContent = "Subject\n"
            + "MIME-Version: 1.0\n"
            + "Content-Type: multipart/alternative;\n"
            + "\tboundary=\"----=_Part_370449_1340169331.1489506420401\"\n"
            + "\n"
            + "------=_Part_370449_1340169331.1489506420401\n"
            + "Content-Type: text/plain; charset=UTF-8\n"
            + "Content-Transfer-Encoding: 7bit\n"
            + "\n"
            + "My plain message\n"
            + "------=_Part_370449_1340169331.1489506420401\n"
            + "Content-Type: text/html; charset=UTF-8\n"
            + "Content-Transfer-Encoding: 7bit\n"
            + "\n"
            + "<a>The </a> <strong>HTML</strong> message";

        ComposedMessageId message = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(messageContent),
            session);

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message.getMessageId()))
            .build();

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .extracting(Message::getId, Message::getTextBody, Message::getHtmlBody)
            .containsOnly(Tuple.tuple(message.getMessageId(), Optional.of("My plain message"), Optional.of("<a>The </a> <strong>HTML</strong> message")));
    }

    @Test
    public void processShouldReturnHeadersFieldWhenSpecificHeadersRequestedInPropertyList() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        String message1Content = "From: user@domain.tld\r\n"
                + "header1: Header1Content\r\n"
                + "HEADer2: Header2Content\r\n"
                + "Subject: message 1 subject\r\n\r\nmy message";
        ComposedMessageId message1 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(message1Content),
            session);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(message1.getMessageId()))
                .properties(ImmutableList.of("headers.from", "headers.heADER2"))
                .build();

        Set<MessageProperty> expected = Sets.newHashSet(MessageProperty.id, MessageProperty.headers);

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProperties())
            .isEqualTo(Optional.of(expected));
    }
    
    @Test
    public void processShouldReturnPropertyFilterWhenFilteringHeadersRequested() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        String message1Content = "From: user@domain.tld\r\n"
                + "header1: Header1Content\r\n"
                + "HEADer2: Header2Content\r\n"
                + "Subject: message 1 subject\r\n\r\nmy message";
        ComposedMessageId message1 = inbox.appendMessage(
            MessageManager.AppendCommand.builder().build(message1Content),
            session);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(ImmutableList.of(message1.getMessageId()))
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

    @Test
    public void processShouldReturnOneMessageWhenMessageInSeveralMailboxes() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        String message1Content = "From: user@domain.tld\r\n"
            + "header1: Header1Content\r\n"
            + "HEADer2: Header2Content\r\n"
            + "Subject: message 1 subject\r\n\r\nmy message";
        ComposedMessageId message1 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(message1Content),
            session);

        MailboxId customMailboxId = mailboxManager.getMailbox(customMailboxPath, session).getId();
        messageIdManager.setInMailboxes(message1.getMessageId(),
            ImmutableList.of(message1.getMailboxId(), customMailboxId),
            session);

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId()))
            .properties(ImmutableList.of("mailboxIds"))
            .build();

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1);
        Method.Response response = result.get(0).getResponse();
        assertThat(response).isInstanceOf(GetMessagesResponse.class);
        GetMessagesResponse getMessagesResponse = (GetMessagesResponse) response;
        assertThat(getMessagesResponse.list()).hasSize(1);
        assertThat(getMessagesResponse.list().get(0).getMailboxIds()).containsOnly(customMailboxId, message1.getMailboxId());
    }

    @Test
    public void processShouldNotFailOnSingleMessageFailure() throws Exception {
        MessageFactory messageFactory = mock(MessageFactory.class);
        testee = new GetMessagesMethod(messageFactory, messageIdManager, new DefaultMetricFactory());
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        String message1Content = "From: user@domain.tld\r\n"
            + "header1: Header1Content\r\n"
            + "HEADer2: Header2Content\r\n"
            + "Subject: message 1 subject\r\n\r\nmy message";
        ComposedMessageId message1 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(message1Content),
            session);
        ComposedMessageId message2 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(message1Content),
            session);
        when(messageFactory.fromMetaDataWithContent(any()))
            .thenReturn(mock(Message.class))
            .thenThrow(new RuntimeException());

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId(), message2.getMessageId()))
            .properties(ImmutableList.of("mailboxIds"))
            .build();

        List<JmapResponse> responses = testee.process(request, clientId, session).collect(Guavate.toImmutableList());

        assertThat(responses).hasSize(1);
        Method.Response response = responses.get(0).getResponse();
        assertThat(response).isInstanceOf(GetMessagesResponse.class);
        GetMessagesResponse getMessagesResponse = (GetMessagesResponse) response;
        assertThat(getMessagesResponse.list()).hasSize(1);
    }

    @Test
    public void processShouldReturnKeywordsForMessageFlags() throws MailboxException {
        Flags flags = FlagsBuilder.builder()
            .add(Flag.ANSWERED, Flag.DRAFT)
            .build();
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        String message1Content = "Subject: message 1 subject\r\n\r\nmy message";
        ComposedMessageId message1 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(flags)
                .build(message1Content),
            session);
        String message2Content = "Subject: message 2 subject\r\n\r\nmy message";
        ComposedMessageId message2 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(flags)
                .build(message2Content),
            session);
        String message3Content = "Great-Header: message 3 subject\r\n\r\nmy message";
        ComposedMessageId message3 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(flags)
                .build(message3Content),
            session);

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId(),
                message2.getMessageId(),
                message3.getMessageId()))
            .build();

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .extracting(Message::getKeywords)
            .containsOnlyElementsOf(
                    ImmutableList.of(
                        ImmutableMap.of(
                            "$Answered", true,
                            "$Draft", true),
                        ImmutableMap.of(
                            "$Answered", true,
                            "$Draft", true),
                        ImmutableMap.of(
                            "$Answered", true,
                            "$Draft", true)));

    }


    @Test
    public void processShouldReturnKeywordsWithoutUnsupportedKeywordsForMessageFlags() throws MailboxException {
        Flags flags1 = FlagsBuilder.builder()
            .add(Flag.ANSWERED, Flag.DRAFT, Flag.DELETED)
            .build();
        Flags flags2 = FlagsBuilder.builder()
            .add(Flag.ANSWERED, Flag.DRAFT)
            .build();
        Flags flags3 = FlagsBuilder.builder()
            .add(Flag.ANSWERED, Flag.DRAFT, Flag.RECENT)
            .build();
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        String message1Content = "Subject: message 1 subject\r\n\r\nmy message";
        ComposedMessageId message1 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(flags1)
                .build(message1Content),
            session);
        String message2Content = "Subject: message 2 subject\r\n\r\nmy message";
        ComposedMessageId message2 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(flags2)
                .build(message2Content),
            session);
        String message3Content = "Great-Header: message 3 subject\r\n\r\nmy message";
        ComposedMessageId message3 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(flags3)
                .build(message3Content),
            session);

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId(),
                message2.getMessageId(),
                message3.getMessageId()))
            .build();

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .extracting(Message::getKeywords)
            .containsOnlyElementsOf(
                    ImmutableList.of(
                        ImmutableMap.of(
                            "$Answered", true,
                            "$Draft", true),
                        ImmutableMap.of(
                            "$Answered", true,
                            "$Draft", true),
                        ImmutableMap.of(
                            "$Answered", true,
                            "$Draft", true)));

    }

    @Test
    public void processShouldReturnKeywordsWithoutForwardedWhenForwardedUserFlagsMessages() throws MailboxException {
        Flags flags = FlagsBuilder.builder()
            .add(Flag.ANSWERED, Flag.DELETED)
            .add(FORWARDED)
            .build();
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        String message1Content = "Subject: message 1 subject\r\n\r\nmy message";
        ComposedMessageId message1 = inbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(flags)
                .build(message1Content),
            session);

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId()))
            .build();

        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .extracting(Message::getKeywords)
            .containsOnlyElementsOf(
                    ImmutableList.of(
                        ImmutableMap.of(
                            "$Answered", true,
                            FORWARDED, true)));
    }
}
