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
package org.apache.james.jmap.draft.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.Username;
import org.apache.james.jmap.draft.model.GetMessagesRequest;
import org.apache.james.jmap.draft.model.GetMessagesResponse;
import org.apache.james.jmap.draft.model.MessageProperties.MessageProperty;
import org.apache.james.jmap.draft.model.MethodCallId;
import org.apache.james.jmap.draft.model.message.view.MessageFastViewFactory;
import org.apache.james.jmap.draft.model.message.view.MessageFullView;
import org.apache.james.jmap.draft.model.message.view.MessageFullViewFactory;
import org.apache.james.jmap.draft.model.message.view.MessageHeaderView;
import org.apache.james.jmap.draft.model.message.view.MessageHeaderViewFactory;
import org.apache.james.jmap.draft.model.message.view.MessageMetadataView;
import org.apache.james.jmap.draft.model.message.view.MessageMetadataViewFactory;
import org.apache.james.jmap.draft.model.message.view.MetaMessageViewFactory;
import org.apache.james.jmap.draft.utils.JsoupHtmlTextExtractor;
import org.apache.james.jmap.memory.projections.MemoryMessageFastViewProjection;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;
import org.assertj.core.api.Condition;
import org.assertj.core.data.MapEntry;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.jayway.jsonpath.JsonPath;

import reactor.core.publisher.Mono;

public class GetMessagesMethodTest {
    private static final String FORWARDED = "forwarded";
    private static final Username ROBERT = Username.of("robert");

    private MessageIdManager messageIdManager;
    private org.apache.james.mime4j.dom.Message messageContent1;
    private org.apache.james.mime4j.dom.Message messageContent2;
    private org.apache.james.mime4j.dom.Message messageContent3;
    private StoreMailboxManager mailboxManager;
    private GetMessagesMethod testee;
    private MailboxSession session;
    private MailboxPath inboxPath;
    private MailboxPath customMailboxPath;
    private MethodCallId methodCallId;
    private MessageMetadataViewFactory messageMetadataViewFactory;

    @Before
    public void setup() throws Exception {
        methodCallId = MethodCallId.of("#0");
        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();
        HtmlTextExtractor htmlTextExtractor = new JsoupHtmlTextExtractor();
        BlobManager blobManager = mock(BlobManager.class);
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();

        session = MailboxSessionUtil.create(ROBERT);
        inboxPath = MailboxPath.inbox(session);
        customMailboxPath = new MailboxPath(inboxPath, "custom");
        mailboxManager.createMailbox(inboxPath, session);
        mailboxManager.createMailbox(customMailboxPath, session);
        messageIdManager = resources.getMessageIdManager();

        messageMetadataViewFactory = spy(new MessageMetadataViewFactory(blobManager, messageIdManager));
        MessageFullViewFactory messageFullViewFactory = new MessageFullViewFactory(blobManager, messageContentExtractor,
            htmlTextExtractor, messageIdManager,
            new MemoryMessageFastViewProjection(new RecordingMetricFactory()));
        MessageFastViewFactory messageFastViewFactory = new MessageFastViewFactory(blobManager, messageIdManager,
            new MemoryMessageFastViewProjection(new RecordingMetricFactory()), messageFullViewFactory);

        MetaMessageViewFactory metaMessageViewFactory = new MetaMessageViewFactory(
            messageFullViewFactory,
            new MessageHeaderViewFactory(blobManager, messageIdManager),
            messageMetadataViewFactory,
            messageFastViewFactory);
        testee = new GetMessagesMethod(metaMessageViewFactory, new DefaultMetricFactory());

        messageContent1 = org.apache.james.mime4j.dom.Message.Builder.of()
            .setSubject("message 1 subject")
            .setBody("my message", StandardCharsets.UTF_8)
            .build();

        messageContent2 = org.apache.james.mime4j.dom.Message.Builder.of()
            .setSubject("message 2 subject")
            .setBody("my message", StandardCharsets.UTF_8)
            .build();

        messageContent3 = org.apache.james.mime4j.dom.Message.Builder.of()
            .addField(new RawField("Great-Header", "message 3 subject"))
            .setBody("my message", StandardCharsets.UTF_8)
            .build();
    }

    @Test
    public void processShouldThrowWhenNullRequest() {
        GetMessagesRequest request = null;
        assertThatThrownBy(() -> testee.processToStream(request, mock(MethodCallId.class), mock(MailboxSession.class))).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenNullSession() {
        MailboxSession mailboxSession = null;
        assertThatThrownBy(() -> testee.processToStream(mock(GetMessagesRequest.class), mock(MethodCallId.class), mailboxSession)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenNullMethodCallId() {
        MethodCallId methodCallId = null;
        assertThatThrownBy(() -> testee.processToStream(mock(GetMessagesRequest.class), methodCallId, mock(MailboxSession.class))).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenRequestHasAccountId() {
        assertThatThrownBy(() -> testee.processToStream(
            GetMessagesRequest.builder().accountId("abc").build(), mock(MethodCallId.class), mock(MailboxSession.class))).isInstanceOf(NotImplementedException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processShouldFetchMessages() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        ComposedMessageId message1 = inbox.appendMessage(AppendCommand.from(messageContent1), session).getId();
        ComposedMessageId message2 = inbox.appendMessage(AppendCommand.from(messageContent2), session).getId();
        ComposedMessageId message3 = inbox.appendMessage(AppendCommand.from(messageContent3), session).getId();

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId(),
                message2.getMessageId(),
                message3.getMessageId()))
            .build();

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .hasOnlyElementsOfType(MessageFullView.class)
            .extracting(MessageFullView.class::cast)
            .extracting(MessageFullView::getId, MessageFullView::getSubject, MessageFullView::getTextBody)
            .containsOnly(
                Tuple.tuple(message1.getMessageId(), "message 1 subject", Optional.of("my message")),
                Tuple.tuple(message2.getMessageId(), "message 2 subject", Optional.of("my message")),
                Tuple.tuple(message3.getMessageId(), "", Optional.of("my message")));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processShouldFetchHtmlMessage() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        ComposedMessageId message = inbox.appendMessage(
            AppendCommand.from(
                org.apache.james.mime4j.dom.Message.Builder.of()
                    .setSubject("message 1 subject")
                    .setBody("my <b>HTML</b> message", "html", StandardCharsets.UTF_8)),
            session).getId();

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message.getMessageId()))
            .build();

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .hasOnlyElementsOfType(MessageFullView.class)
            .extracting(MessageFullView.class::cast)
            .extracting(MessageFullView::getId, MessageFullView::getHtmlBody)
            .containsOnly(Tuple.tuple(message.getMessageId(), Optional.of("my <b>HTML</b> message")));
    }

    @Test
    public void processShouldReturnOnlyMandatoryPropertiesOnEmptyPropertyList() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        ComposedMessageId message1 = inbox.appendMessage(AppendCommand.from(this.messageContent1), session).getId();

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId()))
            .properties(ImmutableList.of())
            .build();

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProperties())
            .isEqualTo(Optional.of(ImmutableSet.of(MessageProperty.id)));
    }

    @Test
    public void processShouldReturnAllPropertiesWhenNoPropertyGiven() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);

        ComposedMessageId message1 = inbox.appendMessage(AppendCommand.from(messageContent1), session).getId();

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId()))
            .build();

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProperties())
            .isEqualTo(Optional.of(MessageProperty.allOutputProperties()));
    }

    @Test
    public void processShouldAddMandatoryPropertiesWhenNotInPropertyList() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);

        ComposedMessageId message1 = inbox.appendMessage(AppendCommand.from(messageContent1), session).getId();

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId()))
            .properties(ImmutableList.of(MessageProperty.subject.asFieldName()))
            .build();

        Set<MessageProperty> expected = Sets.newHashSet(MessageProperty.id, MessageProperty.subject);

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProperties())
            .isEqualTo(Optional.of(expected));
    }

    @Test
    public void processShouldReturnTextBodyWhenBodyInPropertyListAndEmptyHtmlBody() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);

        ComposedMessageId message1 = inbox.appendMessage(AppendCommand.from(messageContent1), session).getId();

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId()))
            .properties(ImmutableList.of(MessageProperty.body.asFieldName()))
            .build();

        Set<MessageProperty> expected = Sets.newHashSet(MessageProperty.id, MessageProperty.textBody);

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProperties())
            .isEqualTo(Optional.of(expected));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processShouldReturnTextBodyWhenEmptyTextBodyAndNotEmptyHtmlBody() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);

        ComposedMessageId message = inbox.appendMessage(
            AppendCommand.from(
                org.apache.james.mime4j.dom.Message.Builder.of()
                    .setSubject("message 1 subject")
                    .setBody("my <b>HTML</b> message", "html", StandardCharsets.UTF_8)),
            session).getId();

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message.getMessageId()))
            .build();

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .hasOnlyElementsOfType(MessageFullView.class)
            .extracting(MessageFullView.class::cast)
            .extracting(MessageFullView::getId, MessageFullView::getTextBody, MessageFullView::getHtmlBody)
            .containsOnly(Tuple.tuple(message.getMessageId(), Optional.of("my HTML message"), Optional.of("my <b>HTML</b> message")));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processShouldReturnEmptyTextBodyAndHtmlBodyWhenThoseAreEmpty() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);

        ComposedMessageId message = inbox.appendMessage(
            AppendCommand.from(org.apache.james.mime4j.dom.Message.Builder.of()
                .setSubject("message 1 subject")
                .setBody("", "html", StandardCharsets.UTF_8)),
            session).getId();

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message.getMessageId()))
            .build();

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .hasOnlyElementsOfType(MessageFullView.class)
            .extracting(MessageFullView.class::cast)
            .extracting(MessageFullView::getId, MessageFullView::getTextBody, MessageFullView::getHtmlBody)
            .containsOnly(Tuple.tuple(message.getMessageId(), Optional.empty(), Optional.of("")));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processShouldNotOverrideTextBodyWhenItIsThere() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);

        ComposedMessageId message = inbox.appendMessage(
            AppendCommand.from(org.apache.james.mime4j.dom.Message.Builder.of()
                .setSubject("message subject")
                .setBody(MultipartBuilder.create()
                    .setSubType("alternative")
                    .addBodyPart(BodyPartBuilder.create()
                        .setBody("My plain message", "plain", StandardCharsets.UTF_8))
                    .addBodyPart(BodyPartBuilder.create()
                        .setBody("<a>The </a> <strong>HTML</strong> message", "html", StandardCharsets.UTF_8))
                    .build())),
            session).getId();

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message.getMessageId()))
            .build();

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .hasOnlyElementsOfType(MessageFullView.class)
            .extracting(MessageFullView.class::cast)
            .extracting(MessageFullView::getId, MessageFullView::getTextBody, MessageFullView::getHtmlBody)
            .containsOnly(Tuple.tuple(message.getMessageId(), Optional.of("My plain message"), Optional.of("<a>The </a> <strong>HTML</strong> message")));
    }

    @Test
    public void processShouldReturnHeadersFieldWhenSpecificHeadersRequestedInPropertyList() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);

        ComposedMessageId message1 = inbox.appendMessage(
            AppendCommand.from(
                org.apache.james.mime4j.dom.Message.Builder.of()
                    .setFrom("user@domain.tld")
                    .setField(new RawField("header1", "Header1Content"))
                    .setField(new RawField("HEADer2", "Header2Content"))
                    .setSubject("message 1 subject")
                    .setBody("my message", StandardCharsets.UTF_8)),
            session).getId();

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId()))
            .properties(ImmutableList.of("headers.from", "headers.heADER2"))
            .build();

        Set<MessageProperty> expected = Sets.newHashSet(MessageProperty.id, MessageProperty.headers);

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProperties())
            .isEqualTo(Optional.of(expected));
    }

    @Test
    public void processShouldReturnPropertyFilterWhenFilteringHeadersRequested() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);

        ComposedMessageId message1 = inbox.appendMessage(
            AppendCommand.from(
                org.apache.james.mime4j.dom.Message.Builder.of()
                    .setFrom("user@domain.tld")
                    .setField(new RawField("header1", "Header1Content"))
                    .setField(new RawField("HEADer2", "Header2Content"))
                    .setSubject("message 1 subject")
                    .setBody("my message", StandardCharsets.UTF_8)),
            session).getId();

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId()))
            .properties(ImmutableList.of("headers.from", "headers.heADER2"))
            .build();

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result)
            .hasSize(1)
            .extracting(JmapResponse::getFilterProvider)
            .are(new Condition<>(Optional::isPresent, "present"));
        SimpleFilterProvider actualFilterProvider = result.get(0).getFilterProvider().get().getRight();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setFilterProvider(actualFilterProvider.setDefaultFilter(SimpleBeanPropertyFilter.serializeAll()));
        String response = objectMapper.writer().writeValueAsString(result.get(0));
        assertThat(JsonPath.parse(response).<Map<String, String>>read("$.response.list[0].headers")).containsOnly(MapEntry.entry("From", "user@domain.tld"), MapEntry.entry("HEADer2", "Header2Content"));
    }

    @Test
    public void processShouldReturnOneMessageWhenMessageInSeveralMailboxes() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);

        ComposedMessageId message1 = inbox.appendMessage(
            AppendCommand.from(
                org.apache.james.mime4j.dom.Message.Builder.of()
                    .setFrom("user@domain.tld")
                    .setField(new RawField("header1", "Header1Content"))
                    .setField(new RawField("HEADer2", "Header2Content"))
                    .setSubject("message 1 subject")
                    .setBody("my message", StandardCharsets.UTF_8)),
            session).getId();

        MailboxId customMailboxId = mailboxManager.getMailbox(customMailboxPath, session).getId();
        messageIdManager.setInMailboxes(message1.getMessageId(),
            ImmutableList.of(message1.getMailboxId(), customMailboxId),
            session);

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId()))
            .properties(ImmutableList.of("mailboxIds"))
            .build();

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1);
        Method.Response response = result.get(0).getResponse();
        assertThat(response).isInstanceOf(GetMessagesResponse.class);
        GetMessagesResponse getMessagesResponse = (GetMessagesResponse) response;
        assertThat(getMessagesResponse.list()).hasSize(1)
            .hasOnlyElementsOfType(MessageMetadataView.class)
            .extracting(MessageMetadataView.class::cast)
            .flatExtracting(MessageMetadataView::getMailboxIds)
            .containsOnly(customMailboxId, message1.getMailboxId());
    }

    @Test
    public void processShouldReturnMetadataWhenOnlyMailboxIds() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);

        ComposedMessageId message1 = inbox.appendMessage(
            AppendCommand.from(
                org.apache.james.mime4j.dom.Message.Builder.of()
                    .setFrom("user@domain.tld")
                    .setField(new RawField("header1", "Header1Content"))
                    .setField(new RawField("HEADer2", "Header2Content"))
                    .setSubject("message 1 subject")
                    .setBody("my message", StandardCharsets.UTF_8)),
            session).getId();

        MailboxId customMailboxId = mailboxManager.getMailbox(customMailboxPath, session).getId();
        messageIdManager.setInMailboxes(message1.getMessageId(),
            ImmutableList.of(message1.getMailboxId(), customMailboxId),
            session);

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId()))
            .properties(ImmutableList.of("mailboxIds"))
            .build();

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1);
        Method.Response response = result.get(0).getResponse();
        assertThat(response).isInstanceOf(GetMessagesResponse.class);
        GetMessagesResponse getMessagesResponse = (GetMessagesResponse) response;
        assertThat(getMessagesResponse.list())
            .hasSize(1)
            .hasOnlyElementsOfType(MessageMetadataView.class);
    }

    @Test
    public void processShouldReturnFullViewWhenRequestedTextBody() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);

        ComposedMessageId message1 = inbox.appendMessage(
            AppendCommand.from(
                org.apache.james.mime4j.dom.Message.Builder.of()
                    .setFrom("user@domain.tld")
                    .setField(new RawField("header1", "Header1Content"))
                    .setField(new RawField("HEADer2", "Header2Content"))
                    .setSubject("message 1 subject")
                    .setBody("my message", StandardCharsets.UTF_8)),
            session).getId();

        MailboxId customMailboxId = mailboxManager.getMailbox(customMailboxPath, session).getId();
        messageIdManager.setInMailboxes(message1.getMessageId(),
            ImmutableList.of(message1.getMailboxId(), customMailboxId),
            session);

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId()))
            .properties(ImmutableList.of("mailboxIds", "textBody"))
            .build();

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1);
        Method.Response response = result.get(0).getResponse();
        assertThat(response).isInstanceOf(GetMessagesResponse.class);
        GetMessagesResponse getMessagesResponse = (GetMessagesResponse) response;
        assertThat(getMessagesResponse.list())
            .hasSize(1)
            .hasOnlyElementsOfType(MessageFullView.class);
    }

    @Test
    public void processShouldReturnHeaderViewWhenRequestedTo() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);

        ComposedMessageId message1 = inbox.appendMessage(
            AppendCommand.from(
                org.apache.james.mime4j.dom.Message.Builder.of()
                    .setFrom("user@domain.tld")
                    .setField(new RawField("header1", "Header1Content"))
                    .setField(new RawField("HEADer2", "Header2Content"))
                    .setSubject("message 1 subject")
                    .setBody("my message", StandardCharsets.UTF_8)),
            session).getId();

        MailboxId customMailboxId = mailboxManager.getMailbox(customMailboxPath, session).getId();
        messageIdManager.setInMailboxes(message1.getMessageId(),
            ImmutableList.of(message1.getMailboxId(), customMailboxId),
            session);

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId()))
            .properties(ImmutableList.of("mailboxIds", "to"))
            .build();

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1);
        Method.Response response = result.get(0).getResponse();
        assertThat(response).isInstanceOf(GetMessagesResponse.class);
        GetMessagesResponse getMessagesResponse = (GetMessagesResponse) response;
        assertThat(getMessagesResponse.list())
            .hasSize(1)
            .hasOnlyElementsOfType(MessageHeaderView.class);
    }

    @Test
    public void processShouldNotFailOnSingleMessageFailure() throws Exception {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);

        org.apache.james.mime4j.dom.Message messageContent = org.apache.james.mime4j.dom.Message.Builder.of()
            .setFrom("user@domain.tld")
            .setField(new RawField("header1", "Header1Content"))
            .setField(new RawField("HEADer2", "Header2Content"))
            .setSubject("message 1 subject")
            .setBody("my message", StandardCharsets.UTF_8)
            .build();

        ComposedMessageId message1 = inbox.appendMessage(AppendCommand.from(messageContent), session).getId();
        ComposedMessageId message2 = inbox.appendMessage(AppendCommand.from(messageContent), session).getId();

        doCallRealMethod()
            .doReturn(Mono.error(new RuntimeException()))
            .when(messageMetadataViewFactory)
            .fromMessageResults(any());

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId(), message2.getMessageId()))
            .properties(ImmutableList.of("mailboxIds"))
            .build();

        List<JmapResponse> responses = testee.processToStream(request, methodCallId, session).collect(ImmutableList.toImmutableList());

        assertThat(responses).hasSize(1);
        Method.Response response = responses.get(0).getResponse();
        assertThat(response).isInstanceOf(GetMessagesResponse.class);
        GetMessagesResponse getMessagesResponse = (GetMessagesResponse) response;
        assertThat(getMessagesResponse.list()).hasSize(1);
    }

    @Test
    public void processShouldReturnKeywordsForMessageFlags() throws Exception {
        Flags flags = FlagsBuilder.builder()
            .add(Flag.ANSWERED, Flag.DRAFT)
            .build();
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        ComposedMessageId message1 = inbox.appendMessage(
            AppendCommand.builder()
                .withFlags(flags)
                .build(messageContent1),
            session).getId();
        ComposedMessageId message2 = inbox.appendMessage(
            AppendCommand.builder()
                .withFlags(flags)
                .build(messageContent2),
            session).getId();
        ComposedMessageId message3 = inbox.appendMessage(
            AppendCommand.builder()
                .withFlags(flags)
                .build(messageContent3),
            session).getId();

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId(),
                message2.getMessageId(),
                message3.getMessageId()))
            .build();

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .hasOnlyElementsOfType(MessageFullView.class)
            .extracting(MessageFullView.class::cast)
            .extracting(MessageFullView::getKeywords)
            .containsOnly( ImmutableMap.of(
                    "$Answered", true,
                    "$Draft", true),
                ImmutableMap.of(
                    "$Answered", true,
                    "$Draft", true),
                ImmutableMap.of(
                    "$Answered", true,
                    "$Draft", true));

    }


    @Test
    public void processShouldReturnKeywordsWithoutUnsupportedKeywordsForMessageFlags() throws Exception {
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
        ComposedMessageId message1 = inbox.appendMessage(
            AppendCommand.builder()
                .withFlags(flags1)
                .build(messageContent1),
            session).getId();
        ComposedMessageId message2 = inbox.appendMessage(
            AppendCommand.builder()
                .withFlags(flags2)
                .build(messageContent2),
            session).getId();
        ComposedMessageId message3 = inbox.appendMessage(
            AppendCommand.builder()
                .withFlags(flags3)
                .build(messageContent3),
            session).getId();

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId(),
                message2.getMessageId(),
                message3.getMessageId()))
            .build();

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .hasOnlyElementsOfType(MessageFullView.class)
            .extracting(MessageFullView.class::cast)
            .extracting(MessageFullView::getKeywords)
            .containsOnly(ImmutableMap.of(
                    "$Answered", true,
                    "$Draft", true),
                ImmutableMap.of(
                    "$Answered", true,
                    "$Draft", true),
                ImmutableMap.of(
                    "$Answered", true,
                    "$Draft", true));

    }

    @Test
    public void processShouldReturnKeywordsWithoutForwardedWhenForwardedUserFlagsMessages() throws Exception {
        Flags flags = FlagsBuilder.builder()
            .add(Flag.ANSWERED, Flag.DELETED)
            .add(FORWARDED)
            .build();
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);

        ComposedMessageId message1 = inbox.appendMessage(
            AppendCommand.builder()
                .withFlags(flags)
                .build(messageContent1),
            session).getId();

        GetMessagesRequest request = GetMessagesRequest.builder()
            .ids(ImmutableList.of(message1.getMessageId()))
            .build();

        List<JmapResponse> result = testee.processToStream(request, methodCallId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .hasOnlyElementsOfType(MessageFullView.class)
            .extracting(MessageFullView.class::cast)
            .extracting(MessageFullView::getKeywords)
            .containsOnly(ImmutableMap.of(
                "$Answered", true,
                FORWARDED, true));
    }

}
