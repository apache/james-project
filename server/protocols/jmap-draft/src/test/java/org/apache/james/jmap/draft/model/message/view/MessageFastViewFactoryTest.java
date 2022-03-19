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

package org.apache.james.jmap.draft.model.message.view;

import static org.apache.james.jmap.draft.model.message.view.MessageViewFixture.ALICE_EMAIL;
import static org.apache.james.jmap.draft.model.message.view.MessageViewFixture.BOB;
import static org.apache.james.jmap.draft.model.message.view.MessageViewFixture.BOB_EMAIL;
import static org.apache.james.jmap.draft.model.message.view.MessageViewFixture.HEADERS_MAP;
import static org.apache.james.jmap.draft.model.message.view.MessageViewFixture.JACK_EMAIL;
import static org.apache.james.jmap.draft.model.message.view.MessageViewFixture.JACOB_EMAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.draft.methods.BlobManagerImpl;
import org.apache.james.jmap.draft.model.BlobId;
import org.apache.james.jmap.draft.model.Keyword;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.model.Number;
import org.apache.james.jmap.draft.model.PreviewDTO;
import org.apache.james.jmap.draft.utils.JsoupHtmlTextExtractor;
import org.apache.james.jmap.memory.projections.MemoryMessageFastViewProjection;
import org.apache.james.jmap.memory.upload.InMemoryUploadRepository;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class MessageFastViewFactoryTest {

    private static final String PREVIEW_1_STRING = "preview 1";
    private static final Preview PREVIEW_1 = Preview.from(PREVIEW_1_STRING);
    private static final String PREVIEW_3_STRING = "preview 3";
    private static final Preview PREVIEW_3 = Preview.from(PREVIEW_3_STRING);
    private static final String PREVIEW_4_STRING = "preview 4";
    private static final Preview PREVIEW_4 = Preview.from(PREVIEW_4_STRING);
    private static final String DEFAULT_PREVIEW_STRING = "blabla bloblo";
    private static final MessageFastViewPrecomputedProperties PROJECTION_1 = MessageFastViewPrecomputedProperties
        .builder()
        .preview(PREVIEW_1)
        .hasAttachment()
        .build();
    private static final MessageFastViewPrecomputedProperties PROJECTION_3 = MessageFastViewPrecomputedProperties
        .builder()
        .preview(PREVIEW_3)
        .noAttachments()
        .build();
    private static final MessageFastViewPrecomputedProperties PROJECTION_4 = MessageFastViewPrecomputedProperties
        .builder()
        .preview(PREVIEW_4)
        .noAttachments()
        .build();

    private MessageIdManager messageIdManager;
    private MailboxSession session;
    private MessageManager bobInbox;
    private ComposedMessageId previewComputedMessage1;
    private ComposedMessageId missingPreviewComputedMessage1;
    private ComposedMessageId previewComputedMessage2;
    private ComposedMessageId previewComputedMessage3;
    private MessageFastViewFactory messageFastViewFactory;
    private MemoryMessageFastViewProjection fastViewProjection;
    private BlobManagerImpl blobManager;
    private MessageFullViewFactory messageFullViewFactory;

    @BeforeEach
    void setUp() throws Exception {
        HtmlTextExtractor htmlTextExtractor = new JsoupHtmlTextExtractor();

        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        messageIdManager = spy(resources.getMessageIdManager());
        InMemoryMailboxManager mailboxManager = resources.getMailboxManager();

        session = mailboxManager.createSystemSession(BOB);
        MailboxId bobInboxId = mailboxManager.createMailbox(MailboxPath.inbox(session), session).get();

        bobInbox = mailboxManager.getMailbox(bobInboxId, session);

        previewComputedMessage1 = bobInbox.appendMessage(MessageManager.AppendCommand.builder()
                .withFlags(new Flags(Flags.Flag.SEEN))
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("fullMessage.eml")),
            session).getId();
        missingPreviewComputedMessage1 = bobInbox.appendMessage(MessageManager.AppendCommand.builder()
                .withFlags(new Flags(Flags.Flag.SEEN))
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("fullMessage.eml")),
            session).getId();
        previewComputedMessage2 = bobInbox.appendMessage(MessageManager.AppendCommand.builder()
                .withFlags(new Flags(Flags.Flag.SEEN))
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("fullMessage.eml")),
            session).getId();
        previewComputedMessage3 = bobInbox.appendMessage(MessageManager.AppendCommand.builder()
                .withFlags(new Flags(Flags.Flag.SEEN))
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("fullMessage.eml")),
            session).getId();

        fastViewProjection = new MemoryMessageFastViewProjection(new RecordingMetricFactory());

        Mono.from(fastViewProjection.store(previewComputedMessage1.getMessageId(), PROJECTION_1))
            .block();
        Mono.from(fastViewProjection.store(previewComputedMessage2.getMessageId(), PROJECTION_3))
            .block();
        Mono.from(fastViewProjection.store(previewComputedMessage3.getMessageId(), PROJECTION_4))
            .block();

        blobManager = new BlobManagerImpl(resources.getAttachmentManager(), resources.getMessageIdManager(), resources.getMessageIdFactory(),
            new InMemoryUploadRepository(new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.of("default"), new HashBlobId.Factory()),
                Clock.systemUTC()));
        messageFullViewFactory = new MessageFullViewFactory(blobManager, messageContentExtractor, htmlTextExtractor, messageIdManager,
            new MemoryMessageFastViewProjection(new RecordingMetricFactory()));
        messageFastViewFactory = new MessageFastViewFactory(blobManager, messageIdManager, fastViewProjection, messageFullViewFactory);
    }

    @Test
    void fromMessageIdsShouldReturnAMessageWithComputedFastProperties() throws Exception {
        MessageFastView actual = messageFastViewFactory.fromMessageIds(ImmutableList.of(previewComputedMessage1.getMessageId()), session).collectList().block().get(0);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(actual.getId()).isEqualTo(previewComputedMessage1.getMessageId());
            softly.assertThat(actual.getMailboxIds()).containsExactly(bobInbox.getId());
            softly.assertThat(actual.getThreadId()).isEqualTo(previewComputedMessage1.getMessageId().serialize());
            softly.assertThat(actual.getSize()).isEqualTo(Number.fromLong(2255));
            softly.assertThat(actual.getKeywords()).isEqualTo(Keywords.strictFactory().from(Keyword.SEEN).asMap());
            softly.assertThat(actual.getBlobId()).isEqualTo(BlobId.of(previewComputedMessage1.getMessageId().serialize()));
            softly.assertThat(actual.getInReplyToMessageId()).isEqualTo(Optional.of(BOB.asString()));
            softly.assertThat(actual.getHeaders()).isEqualTo(HEADERS_MAP);
            softly.assertThat(actual.getFrom()).isEqualTo(Optional.of(ALICE_EMAIL));
            softly.assertThat(actual.getTo()).isEqualTo(ImmutableList.of(BOB_EMAIL));
            softly.assertThat(actual.getCc()).isEqualTo(ImmutableList.of(JACK_EMAIL, JACOB_EMAIL));
            softly.assertThat(actual.getBcc()).isEqualTo(ImmutableList.of(ALICE_EMAIL));
            softly.assertThat(actual.getReplyTo()).isEqualTo(ImmutableList.of(ALICE_EMAIL));
            softly.assertThat(actual.getSubject()).isEqualTo("Full message");
            softly.assertThat(actual.getDate()).isEqualTo("2016-06-07T14:23:37Z");

            softly.assertThat(actual.isHasAttachment()).isTrue();
            softly.assertThat(actual.getPreview()).isEqualTo(PreviewDTO.of(PREVIEW_1_STRING));
        });
    }

    @Test
    void fromMessageIdsShouldReturnAMessageWithPropertiesComputedFromFullMessageWhenNotPreComputed() throws Exception {
        MessageFastView actual = messageFastViewFactory.fromMessageIds(ImmutableList.of(missingPreviewComputedMessage1.getMessageId()), session).collectList().block().get(0);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(actual.getId()).isEqualTo(missingPreviewComputedMessage1.getMessageId());
            softly.assertThat(actual.getMailboxIds()).containsExactly(bobInbox.getId());
            softly.assertThat(actual.getThreadId()).isEqualTo(missingPreviewComputedMessage1.getMessageId().serialize());
            softly.assertThat(actual.getSize()).isEqualTo(Number.fromLong(2255));
            softly.assertThat(actual.getKeywords()).isEqualTo(Keywords.strictFactory().from(Keyword.SEEN).asMap());
            softly.assertThat(actual.getBlobId()).isEqualTo(BlobId.of(missingPreviewComputedMessage1.getMessageId().serialize()));
            softly.assertThat(actual.getInReplyToMessageId()).isEqualTo(Optional.of(BOB.asString()));
            softly.assertThat(actual.getHeaders()).isEqualTo(HEADERS_MAP);
            softly.assertThat(actual.getFrom()).isEqualTo(Optional.of(ALICE_EMAIL));
            softly.assertThat(actual.getTo()).isEqualTo(ImmutableList.of(BOB_EMAIL));
            softly.assertThat(actual.getCc()).isEqualTo(ImmutableList.of(JACK_EMAIL, JACOB_EMAIL));
            softly.assertThat(actual.getBcc()).isEqualTo(ImmutableList.of(ALICE_EMAIL));
            softly.assertThat(actual.getReplyTo()).isEqualTo(ImmutableList.of(ALICE_EMAIL));
            softly.assertThat(actual.getSubject()).isEqualTo("Full message");
            softly.assertThat(actual.getDate()).isEqualTo("2016-06-07T14:23:37Z");

            softly.assertThat(actual.isHasAttachment()).isTrue();
            softly.assertThat(actual.getPreview()).isEqualTo(PreviewDTO.of(DEFAULT_PREVIEW_STRING));
        });
    }

    @Test
    void fromMessageIdsShouldReturnMessagesWithPreviews() throws Exception {
        List<MessageFastView> actual = messageFastViewFactory
            .fromMessageIds(ImmutableList.of(
                    previewComputedMessage2.getMessageId(),
                    previewComputedMessage3.getMessageId(),
                    missingPreviewComputedMessage1.getMessageId(),
                    previewComputedMessage1.getMessageId()),
                session).collectList().block();

        assertThat(actual)
            .hasSize(4)
            .extracting(MessageFastView::getPreview)
            .containsOnly(
                PreviewDTO.of(PREVIEW_3_STRING),
                PreviewDTO.of(PREVIEW_4_STRING),
                PreviewDTO.of(DEFAULT_PREVIEW_STRING),
                PreviewDTO.of(PREVIEW_1_STRING));
    }

    @Test
    void fromMessageIdsShouldKeepProcessingEvenWhenFetchingFail() {
        doReturn(Flux.error(new MailboxException("mock exception")))
            .doCallRealMethod()
            .when(messageIdManager).getMessagesReactive(any(), any(), any());

        List<MessageFastView> actual = messageFastViewFactory
            .fromMessageIds(ImmutableList.of(
                    missingPreviewComputedMessage1.getMessageId(),
                    previewComputedMessage1.getMessageId()),
                session)
            .collectList().block();


        assertThat(actual)
            .hasSize(1)
            .extracting(MessageFastView::getId)
            .containsAnyOf(missingPreviewComputedMessage1.getMessageId(), previewComputedMessage1.getMessageId());
    }
}