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

package org.apache.james.mailbox.store;

import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_APPENDED;
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_DELIVERY;
import static org.apache.james.mailbox.extension.PreDeletionHook.DeleteOperation;
import static org.apache.james.mailbox.store.mail.AbstractMessageMapper.UNLIMITED;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.io.input.UnsynchronizedBufferedInputStream;
import org.apache.commons.io.input.UnsynchronizedFilterInputStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManager.MessageCapabilities;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.ReadOnlyException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.FetchGroupConverter;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.quota.QuotaChecker;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.streaming.CountingInputStream;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.field.LenientFieldParser;
import org.apache.james.mime4j.message.DefaultBodyDescriptorBuilder;
import org.apache.james.mime4j.message.HeaderImpl;
import org.apache.james.mime4j.message.MaximalBodyDescriptor;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.apache.james.mime4j.stream.RecursionMode;
import org.apache.james.util.io.BodyOffsetInputStream;
import org.apache.james.util.io.InputStreamConsummer;
import org.apache.james.util.streams.Iterators;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Base class for {@link MessageManager}
 * implementations.
 * 
 * This base class take care of dispatching events to the registered
 * {@link EventListener} and so help with handling concurrent
 * {@link MailboxSession}'s.
 */
public class StoreMessageManager implements MessageManager {
    /**
     * The minimal Permanent flags the {@link MessageManager} must support. <br>
     * 
     * <strong>Be sure this static instance will never get modifed
     * later!</strong>
     */
    protected static final Flags MINIMAL_PERMANET_FLAGS;
    private static final SearchQuery LIST_ALL_QUERY = SearchQuery.of(SearchQuery.all());
    private static final SearchQuery LIST_FROM_ONE = SearchQuery.of(SearchQuery.uid(new SearchQuery.UidRange(MessageUid.MIN_VALUE, MessageUid.MAX_VALUE)));
    private static final LenientFieldParser FIELD_PARSER = new LenientFieldParser();

    private static class MediaType {
        final String mediaType;
        final String subType;

        private MediaType(String mediaType, String subType) {
            this.mediaType = mediaType;
            this.subType = subType;
        }
    }

    static {
        MINIMAL_PERMANET_FLAGS = new Flags();
        MINIMAL_PERMANET_FLAGS.add(Flags.Flag.ANSWERED);
        MINIMAL_PERMANET_FLAGS.add(Flags.Flag.DELETED);
        MINIMAL_PERMANET_FLAGS.add(Flags.Flag.DRAFT);
        MINIMAL_PERMANET_FLAGS.add(Flags.Flag.FLAGGED);
        MINIMAL_PERMANET_FLAGS.add(Flags.Flag.SEEN);
    }

    private final EnumSet<MailboxManager.MessageCapabilities> messageCapabilities;
    private final EventBus eventBus;
    private final Mailbox mailbox;
    private final MailboxSessionMapperFactory mapperFactory;
    private final MessageSearchIndex index;
    private final StoreRightManager storeRightManager;
    private final QuotaManager quotaManager;
    private final QuotaRootResolver quotaRootResolver;
    private final MailboxPathLocker locker;
    private final BatchSizes batchSizes;
    private final PreDeletionHooks preDeletionHooks;
    private final MessageStorer messageStorer;

    public StoreMessageManager(EnumSet<MessageCapabilities> messageCapabilities, MailboxSessionMapperFactory mapperFactory,
                               MessageSearchIndex index, EventBus eventBus,
                               MailboxPathLocker locker, Mailbox mailbox,
                               QuotaManager quotaManager, QuotaRootResolver quotaRootResolver, BatchSizes batchSizes,
                               StoreRightManager storeRightManager, PreDeletionHooks preDeletionHooks, MessageStorer messageStorer) {
        this.messageCapabilities = messageCapabilities;
        this.eventBus = eventBus;
        this.mailbox = mailbox;
        this.mapperFactory = mapperFactory;
        this.index = index;
        this.locker = locker;
        this.quotaManager = quotaManager;
        this.quotaRootResolver = quotaRootResolver;
        this.batchSizes = batchSizes;
        this.storeRightManager = storeRightManager;
        this.preDeletionHooks = preDeletionHooks;
        this.messageStorer = messageStorer;
    }

    /**
     * Return the underlying {@link Mailbox}
     */

    public Mailbox getMailboxEntity() {
        return mailbox;
    }

    /**
     * Return {@link Flags} which are permanent stored by the mailbox. By
     * default this are the following flags: <br>
     * {@link Flag#ANSWERED}, {@link Flag#DELETED}, {@link Flag#DRAFT},
     * {@link Flag#FLAGGED}, {@link Flag#RECENT}, {@link Flag#SEEN} <br>
     * 
     * Which in fact does not allow to permanent store user flags / keywords.
     * 
     * If the sub-class does allow to store "any" user flag / keyword it MUST
     * override this method and add {@link Flag#USER} to the list of returned
     * {@link Flags}. If only a special set of user flags / keywords should be
     * allowed just add them directly.
     */
    public Flags getPermanentFlags(MailboxSession session) {

        // Return a new flags instance to make sure the static declared flags
        // instance will not get modified later.
        //
        // See MAILBOX-109
        return new Flags(MINIMAL_PERMANET_FLAGS);
    }


    @Override
    public MailboxCounters getMailboxCounters(MailboxSession mailboxSession) throws MailboxException {
        if (storeRightManager.hasRight(mailbox, MailboxACL.Right.Read, mailboxSession)) {
            return mapperFactory.createMessageMapper(mailboxSession).getMailboxCounters(mailbox);
        }
        return MailboxCounters.empty(mailbox.getMailboxId());
    }


    @Override
    public Publisher<MailboxCounters> getMailboxCountersReactive(MailboxSession mailboxSession) {
        if (storeRightManager.hasRight(mailbox, MailboxACL.Right.Read, mailboxSession)) {
            return mapperFactory.createMessageMapper(mailboxSession).getMailboxCountersReactive(mailbox);
        }
        return Mono.just(MailboxCounters.empty(mailbox.getMailboxId()));
    }

    /**
     * Returns the flags which are shared for the current mailbox, i.e. the
     * flags set up so that changes to those flags are visible to another user.
     * See RFC 4314 section 5.2.
     * 
     * In this implementation, all permanent flags are shared, ergo we simply
     * return {@link #getPermanentFlags(MailboxSession)}
     */
    protected Flags getSharedPermanentFlags(MailboxSession session) {
        return getPermanentFlags(session);
    }

    @Override
    public Iterator<MessageUid> expunge(MessageRange set, MailboxSession mailboxSession) throws MailboxException {
        if (!isWriteable(mailboxSession)) {
            throw new ReadOnlyException(getMailboxPath());
        }

        List<MessageUid> uids = MailboxReactorUtils.block(retrieveMessagesMarkedForDeletion(set, mailboxSession));
        Map<MessageUid, MessageMetaData> deletedMessages = MailboxReactorUtils.block(deleteMessages(uids, mailboxSession));

        dispatchExpungeEvent(mailboxSession, deletedMessages).block();
        return deletedMessages.keySet().iterator();
    }

    @Override
    public Flux<MessageUid> expungeReactive(MessageRange set, MailboxSession mailboxSession) {
        if (!isWriteable(mailboxSession)) {
            return Flux.error(new ReadOnlyException(getMailboxEntity().generateAssociatedPath()));
        }

        return retrieveMessagesMarkedForDeletion(set, mailboxSession)
            .flatMap(uids -> deleteMessages(uids, mailboxSession))
            .flatMap(deletedMessages -> dispatchExpungeEvent(mailboxSession, deletedMessages).thenReturn(deletedMessages))
            .flatMapIterable(Map::keySet);
    }

    private Mono<List<MessageUid>> retrieveMessagesMarkedForDeletion(MessageRange messageRange, MailboxSession session) {
        MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        return messageMapper.executeReactive(
            messageMapper.retrieveMessagesMarkedForDeletionReactive(getMailboxEntity(), messageRange).collectList());
    }

    @Override
    public void delete(List<MessageUid> messageUids, MailboxSession mailboxSession) throws MailboxException {
        Map<MessageUid, MessageMetaData> deletedMessages = MailboxReactorUtils.block(deleteMessages(messageUids, mailboxSession));

        dispatchExpungeEvent(mailboxSession, deletedMessages).block();
    }

    @Override
    public Mono<Void> deleteReactive(List<MessageUid> uids, MailboxSession mailboxSession) {
        return deleteMessages(uids, mailboxSession)
            .flatMap(deleteMessages -> dispatchExpungeEvent(mailboxSession, deleteMessages));
    }

    private Mono<Map<MessageUid, MessageMetaData>> deleteMessages(List<MessageUid> messageUids, MailboxSession session) {
        if (messageUids.isEmpty()) {
            return Mono.just(ImmutableMap.of());
        }

        MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        return runPredeletionHooks(messageUids, session)
            .then(messageMapper.executeReactive(messageMapper.deleteMessagesReactive(getMailboxEntity(), messageUids)));

    }

    private Mono<Void> dispatchExpungeEvent(MailboxSession mailboxSession, Map<MessageUid, MessageMetaData> deletedMessages) {
        return eventBus.dispatch(EventFactory.expunged()
                .randomEventId()
                .mailboxSession(mailboxSession)
                .mailbox(getMailboxEntity())
                .metaData(ImmutableSortedMap.copyOf(deletedMessages))
                .build(),
            new MailboxIdRegistrationKey(mailbox.getMailboxId()));
    }

    @Override
    public AppendResult appendMessage(AppendCommand appendCommand, MailboxSession session) throws MailboxException {
        return MailboxReactorUtils.block(appendMessage(
            appendCommand.getMsgIn(),
            appendCommand.getInternalDate(),
            session,
            appendCommand.isRecent(),
            appendCommand.getFlags(),
            appendCommand.getMaybeParsedMessage(),
            appendCommand.isDelivery()));
    }

    @Override
    public Publisher<AppendResult> appendMessageReactive(AppendCommand appendCommand, MailboxSession session) {
        return appendMessage(
            appendCommand.getMsgIn(),
            appendCommand.getInternalDate(),
            session,
            appendCommand.isRecent(),
            appendCommand.getFlags(),
            appendCommand.getMaybeParsedMessage(),
            appendCommand.isDelivery());
    }

    @Override
    public AppendResult appendMessage(InputStream msgIn, Date internalDate, final MailboxSession mailboxSession, boolean isRecent, Flags flagsToBeSet) throws MailboxException {
        File file = null;

        if (!isWriteable(mailboxSession)) {
            throw new ReadOnlyException(getMailboxPath());
        }

        try {
            // Create a temporary file and copy the message to it. We will work
            // with the file as
            // source for the InputStream
            file = Files.createTempFile("imap", ".msg").toFile();
            try (FileOutputStream out = new FileOutputStream(file);
                 BufferedOutputStream bufferedOut = new BufferedOutputStream(out);
                 UnsynchronizedFilterInputStream tmpMsgIn = UnsynchronizedBufferedInputStream.builder()
                     .setInputStream(new TeeInputStream(msgIn, bufferedOut))
                     .get();
                 BodyOffsetInputStream bIn = new BodyOffsetInputStream(tmpMsgIn)) {
                Pair<PropertyBuilder, HeaderImpl> pair = parseProperties(bIn);
                PropertyBuilder propertyBuilder = pair.getLeft();
                HeaderImpl headers = pair.getRight();

                InputStreamConsummer.consume(tmpMsgIn);
                bufferedOut.flush();
                int bodyStartOctet = getBodyStartOctet(bIn);
                File finalFile = file;
                Optional<Message> unparsedMimeMessqage = Optional.empty();
                return MailboxReactorUtils.block(createAndDispatchMessage(computeInternalDate(internalDate),
                    mailboxSession, new Content() {
                        @Override
                        public InputStream getInputStream() throws IOException {
                            return new FileInputStream(finalFile);
                        }

                        @Override
                        public long size() {
                            return finalFile.length();
                        }
                    }, propertyBuilder,
                    getFlags(mailboxSession, isRecent, flagsToBeSet), bodyStartOctet, unparsedMimeMessqage, headers, !IS_DELIVERY));
            }
        } catch (IOException | MimeException e) {
            throw new MailboxException("Unable to parse message", e);
        } finally {
            // delete the temporary file if one was specified
            if (file != null) {
                if (!file.delete()) {
                    // Don't throw an IOException. The message could be appended
                    // and the temporary file
                    // will be deleted hopefully some day
                }
            }
        }
    }

    private Mono<AppendResult> appendMessage(Content msgIn, Date internalDate, final MailboxSession mailboxSession, boolean isRecent, Flags flagsToBeSet,
                                             Optional<Message> maybeMessage, boolean isDelivery) {
        return Mono.fromCallable(() -> {
            if (!isWriteable(mailboxSession)) {
                throw new ReadOnlyException(getMailboxPath());
            }

            try (InputStream contentStream = msgIn.getInputStream();
                 UnsynchronizedFilterInputStream bufferedContentStream = UnsynchronizedBufferedInputStream.builder()
                     .setInputStream(contentStream)
                     .get();
                BodyOffsetInputStream bIn = new BodyOffsetInputStream(bufferedContentStream)) {
                Pair<PropertyBuilder, HeaderImpl> pair = parseProperties(bIn);
                PropertyBuilder propertyBuilder = pair.getLeft();
                HeaderImpl headers = pair.getRight();
                int bodyStartOctet = getBodyStartOctet(bIn);

                return createAndDispatchMessage(computeInternalDate(internalDate),
                    mailboxSession, msgIn, propertyBuilder,
                    getFlags(mailboxSession, isRecent, flagsToBeSet), bodyStartOctet, maybeMessage, headers, isDelivery);
            } catch (IOException | MimeException e) {
                throw new MailboxException("Unable to parse message", e);
            }
        }).flatMap(Function.identity())
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Pair<PropertyBuilder, HeaderImpl> parseProperties(BodyOffsetInputStream bIn) throws IOException, MimeException {
        // Disable line length... This should be handled by the smtp server
        // component and not the parser itself
        // https://issues.apache.org/jira/browse/IMAP-122
        MimeTokenStream parser = getParser(bIn);
        final HeaderImpl headers = readHeader(parser);

        final MaximalBodyDescriptor descriptor = (MaximalBodyDescriptor) parser.getBodyDescriptor();
        final MediaType mediaType = getMediaType(descriptor);
        final PropertyBuilder propertyBuilder = getPropertyBuilder(descriptor, mediaType.mediaType, mediaType.subType);
        setTextualLinesCount(parser, mediaType.mediaType, propertyBuilder);
        return new ImmutablePair<>(propertyBuilder, headers);
    }

    private Date computeInternalDate(Date internalDate) {
        return Optional.ofNullable(internalDate)
            .orElseGet(Date::new);
    }

    private MimeTokenStream getParser(BodyOffsetInputStream bIn) {
        final MimeTokenStream parser = new MimeTokenStream(MimeConfig.PERMISSIVE,
            new DefaultBodyDescriptorBuilder(null, FIELD_PARSER, DecodeMonitor.SILENT));

        parser.setRecursionMode(RecursionMode.M_NO_RECURSE);
        parser.parse(bIn);
        return parser;
    }

    private MediaType getMediaType(MaximalBodyDescriptor descriptor) {
        final String mediaTypeFromHeader = descriptor.getMediaType();
        if (mediaTypeFromHeader == null) {
            return new MediaType("text", "plain");
        } else {
            return new MediaType(mediaTypeFromHeader, descriptor.getSubType());
        }
    }

    private HeaderImpl readHeader(MimeTokenStream parser) throws IOException, MimeException {
        final HeaderImpl header = new HeaderImpl();

        EntityState next = parser.next();
        while (next != EntityState.T_BODY && next != EntityState.T_END_OF_STREAM && next != EntityState.T_START_MULTIPART) {
            if (next == EntityState.T_FIELD) {
                header.addField(parser.getField());
            }
            next = parser.next();
        }
        return header;
    }

    private Flags getFlags(MailboxSession mailboxSession, boolean isRecent, Flags flagsToBeSet) {
        final Flags flags;
        if (flagsToBeSet == null) {
            flags = new Flags();
        } else {
            flags = flagsToBeSet;

            // Check if we need to trim the flags
            trimFlags(flags, mailboxSession);

        }
        if (isRecent) {
            flags.add(Flag.RECENT);
        }
        return flags;
    }

    private void setTextualLinesCount(MimeTokenStream parser, String mediaType, PropertyBuilder propertyBuilder) throws IOException, MimeException {
        EntityState next;
        if ("text".equalsIgnoreCase(mediaType)) {
            final CountingInputStream bodyStream = new CountingInputStream(parser.getInputStream());
            bodyStream.readAll();
            long lines = bodyStream.getLineCount();
            bodyStream.close();
            next = parser.next();
            if (next == EntityState.T_EPILOGUE) {
                final CountingInputStream epilogueStream = new CountingInputStream(parser.getInputStream());
                epilogueStream.readAll();
                lines += epilogueStream.getLineCount();
                epilogueStream.close();

            }
            propertyBuilder.setTextualLineCount(lines);
        }
    }

    private int getBodyStartOctet(BodyOffsetInputStream bIn) {
        int bodyStartOctet = (int) bIn.getBodyStartOffset();
        if (bodyStartOctet == -1) {
            bodyStartOctet = 0;
        }
        return bodyStartOctet;
    }

    private Mono<AppendResult> createAndDispatchMessage(Date internalDate, MailboxSession mailboxSession, Content content, PropertyBuilder propertyBuilder,
                                                        Flags flags, int bodyStartOctet, Optional<Message> maybeMessage, HeaderImpl headers,
                                                        boolean isDelivery) throws MailboxException {
        int size = (int) content.size();
        QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(mailbox);
        return Mono.from(quotaManager.getQuotasReactive(quotaRoot))
            .map(quotas -> new QuotaChecker(quotas, quotaRoot))
            .doOnNext(Throwing.consumer((QuotaChecker quotaChecker) -> quotaChecker.tryAddition(1, size)).sneakyThrow())
            .then(Mono.from(locker.executeReactiveWithLockReactive(getMailboxPath(),
                messageStorer.appendMessageToStore(mailbox, internalDate, size, bodyStartOctet, content, flags, propertyBuilder, maybeMessage, mailboxSession, headers)
                    .flatMap(data -> eventBus.dispatch(EventFactory.added()
                            .randomEventId()
                            .mailboxSession(mailboxSession)
                            .mailbox(mailbox)
                            .addMetaData(data.getLeft())
                            .isDelivery(isDelivery)
                            .isAppended(true)
                            .build(),
                        new MailboxIdRegistrationKey(mailbox.getMailboxId()))
                        .thenReturn(computeAppendResult(data, mailbox))),
                MailboxPathLocker.LockType.Write)));
    }

    private AppendResult computeAppendResult(Pair<MessageMetaData, Optional<List<MessageAttachmentMetadata>>> data, Mailbox mailbox) {
        MessageMetaData messageMetaData = data.getLeft();
        ComposedMessageId ids = new ComposedMessageId(mailbox.getMailboxId(), messageMetaData.getMessageId(), messageMetaData.getUid());
        return new AppendResult(ids, messageMetaData.getSize(), data.getRight(), messageMetaData.getThreadId());
    }

    private PropertyBuilder getPropertyBuilder(MaximalBodyDescriptor descriptor, String mediaType, String subType) {
        final PropertyBuilder propertyBuilder = new PropertyBuilder();
        propertyBuilder.setMediaType(mediaType);
        propertyBuilder.setSubType(subType);
        propertyBuilder.setContentID(descriptor.getContentId());
        propertyBuilder.setContentDescription(descriptor.getContentDescription());
        propertyBuilder.setContentLocation(descriptor.getContentLocation());
        propertyBuilder.setContentMD5(descriptor.getContentMD5Raw());
        propertyBuilder.setContentTransferEncoding(descriptor.getTransferEncoding());
        propertyBuilder.setContentLanguage(descriptor.getContentLanguage());
        propertyBuilder.setContentDispositionType(descriptor.getContentDispositionType());
        propertyBuilder.setContentDispositionParameters(descriptor.getContentDispositionParameters());
        propertyBuilder.setContentTypeParameters(descriptor.getContentTypeParameters());
        // Add missing types
        final String codeset = descriptor.getCharset();
        if (codeset == null) {
            if ("TEXT".equalsIgnoreCase(mediaType)) {
                propertyBuilder.setCharset("us-ascii");
            }
        } else {
            propertyBuilder.setCharset(codeset);
        }
        return propertyBuilder;
    }

    @Override
    public boolean isWriteable(MailboxSession session) {
        return storeRightManager.isReadWrite(session, mailbox, getSharedPermanentFlags(session));
    }

    @Override
    public Mono<MailboxMetaData> getMetaDataReactive(RecentMode recentMode, MailboxSession mailboxSession, EnumSet<MailboxMetaData.Item> items) throws MailboxException {
        MailboxACL resolvedAcl = getResolvedAcl(mailboxSession);
        if (!storeRightManager.hasRight(mailbox, MailboxACL.Right.Read, mailboxSession)) {
            return Mono.just(MailboxMetaData.sensibleInformationFree(resolvedAcl, getMailboxEntity().getUidValidity(), isWriteable(mailboxSession)));
        }
        Flags permanentFlags = getPermanentFlags(mailboxSession);
        UidValidity uidValidity = getMailboxEntity().getUidValidity();
        MessageMapper messageMapper = mapperFactory.getMessageMapper(mailboxSession);

        return messageMapper.executeReactive(
                Mono.zip(nextUid(messageMapper, items),
                highestModSeq(messageMapper, items),
                firstUnseen(messageMapper, items),
                mailboxCounters(messageMapper, items),
                recent(recentMode, mailboxSession))
            .map(t5 -> new MailboxMetaData(t5.getT5(), permanentFlags, uidValidity, t5.getT1(), t5.getT2(), t5.getT4().getCount(),
                t5.getT4().getUnseen(), t5.getT3().orElse(null), isWriteable(mailboxSession), resolvedAcl)));
    }

    protected Mono<ModSeq> highestModSeq(MessageMapper messageMapper, EnumSet<MailboxMetaData.Item> items) {
        if (items.contains(MailboxMetaData.Item.HighestModSeq)) {
            return messageMapper.getHighestModSeqReactive(mailbox);
        }
        return Mono.just(ModSeq.first());
    }

    protected Mono<MessageUid> nextUid(MessageMapper messageMapper, EnumSet<MailboxMetaData.Item> items) {
        if (items.contains(MailboxMetaData.Item.NextUid)) {
            return messageMapper.getLastUidReactive(mailbox)
                .map(optional -> optional
                    .map(MessageUid::next)
                    .orElse(MessageUid.MIN_VALUE));
        }
        return Mono.just(MessageUid.MIN_VALUE);
    }

    protected Mono<Optional<MessageUid>> firstUnseen(MessageMapper messageMapper, EnumSet<MailboxMetaData.Item> items) {
        if (items.contains(MailboxMetaData.Item.FirstUnseen)) {
            return messageMapper.findFirstUnseenMessageUidReactive(getMailboxEntity());
        }
        return Mono.just(Optional.empty());
    }

    protected Mono<MailboxCounters> mailboxCounters(MessageMapper messageMapper, EnumSet<MailboxMetaData.Item> items) {
        if (items.contains(MailboxMetaData.Item.MailboxCounters)) {
            return messageMapper.getMailboxCountersReactive(getMailboxEntity());
        }
        return Mono.just(MailboxCounters.empty(getId()));
    }

    @Override
    public MailboxMetaData getMetaData(RecentMode resetRecent, MailboxSession mailboxSession, EnumSet<MailboxMetaData.Item> items) throws MailboxException {
        return MailboxReactorUtils.block(getMetaDataReactive(resetRecent, mailboxSession, items));
    }

    @Override
    public MailboxACL getResolvedAcl(MailboxSession mailboxSession) throws UnsupportedRightException {
        return storeRightManager.getResolvedMailboxACL(mailbox, mailboxSession);
    }

    /**
     * Check if the given {@link Flags} contains {@link Flags} which are not
     * included in the returned {@link Flags} of
     * {@link #getPermanentFlags(MailboxSession)}. If any are found, these are
     * removed from the given {@link Flags} instance. The only exception is the
     * {@link Flag#RECENT} flag.
     * 
     * This flag is never removed!
     */
    private void trimFlags(Flags flags, MailboxSession session) {

        Flags permFlags = getPermanentFlags(session);

        Flag[] systemFlags = flags.getSystemFlags();
        for (Flag f : systemFlags) {
            if (f != Flag.RECENT && permFlags.contains(f) == false) {
                flags.remove(f);
            }
        }
        // if the permFlags contains the special USER flag we can skip this as
        // all user flags are allowed
        if (permFlags.contains(Flags.Flag.USER) == false) {
            String[] uFlags = flags.getUserFlags();
            for (String uFlag : uFlags) {
                if (permFlags.contains(uFlag) == false) {
                    flags.remove(uFlag);
                }
            }
        }

    }

    @Override
    public Map<MessageUid, Flags> setFlags(final Flags flags, final FlagsUpdateMode flagsUpdateMode, final MessageRange set, MailboxSession mailboxSession) throws MailboxException {

        if (!isWriteable(mailboxSession)) {
            throw new ReadOnlyException(getMailboxPath());
        }

        trimFlags(flags, mailboxSession);

        MessageMapper messageMapper = mapperFactory.getMessageMapper(mailboxSession);

        Iterator<UpdatedFlags> it = messageMapper.execute(() -> messageMapper.updateFlags(getMailboxEntity(), new FlagsUpdateCalculator(flags, flagsUpdateMode), set));
        List<UpdatedFlags> updatedFlags = Iterators.toStream(it).collect(ImmutableList.toImmutableList());

        eventBus.dispatch(EventFactory.flagsUpdated()
                .randomEventId()
                .mailboxSession(mailboxSession)
                .mailbox(getMailboxEntity())
                .updatedFlags(updatedFlags)
                .build(),
                new MailboxIdRegistrationKey(mailbox.getMailboxId()))
            .block();

        return updatedFlags.stream().collect(ImmutableMap.toImmutableMap(
            UpdatedFlags::getUid,
            UpdatedFlags::getNewFlags));
    }

    @Override
    public Publisher<Map<MessageUid, Flags>> setFlagsReactive(Flags flags, FlagsUpdateMode flagsUpdateMode, MessageRange set, MailboxSession mailboxSession) {
        if (!isWriteable(mailboxSession)) {
            return Mono.error(new ReadOnlyException(getMailboxPath()));
        }

        trimFlags(flags, mailboxSession);

        MessageMapper messageMapper = mapperFactory.getMessageMapper(mailboxSession);

        return messageMapper.executeReactive(messageMapper.updateFlagsReactive(getMailboxEntity(), new FlagsUpdateCalculator(flags, flagsUpdateMode), set))
            .flatMap(updatedFlags -> eventBus.dispatch(EventFactory.flagsUpdated()
                    .randomEventId()
                    .mailboxSession(mailboxSession)
                    .mailbox(getMailboxEntity())
                    .updatedFlags(updatedFlags)
                    .build(),
                new MailboxIdRegistrationKey(mailbox.getMailboxId()))
                .thenReturn(updatedFlags.stream().collect(ImmutableMap.toImmutableMap(
                    UpdatedFlags::getUid,
                    UpdatedFlags::getNewFlags))));
    }

    /**
     * Copy the {@link MessageRange} to the {@link StoreMessageManager}
     */
    public Mono<List<MessageRange>> copyTo(MessageRange set, StoreMessageManager toMailbox, MailboxSession session) {
        if (!toMailbox.isWriteable(session)) {
            return Mono.error(new ReadOnlyException(toMailbox.getMailboxPath()));
        }
        //TODO lock the from mailbox too, in a non-deadlocking manner - how?
        return Mono.from(locker.executeReactiveWithLockReactive(toMailbox.getMailboxPath(),
            copy(set, toMailbox, session)
                .map(map -> MessageRange.toRanges(new ArrayList<>(map.keySet()))),
            MailboxPathLocker.LockType.Write));
    }

    /**
     * Move the {@link MessageRange} to the {@link StoreMessageManager}
     */
    public Mono<List<MessageRange>> moveTo(MessageRange set, StoreMessageManager toMailbox, MailboxSession session) {
        if (!isWriteable(session)) {
            return Mono.error(new ReadOnlyException(toMailbox.getMailboxPath()));
        }
        if (!toMailbox.isWriteable(session)) {
            return Mono.error(new ReadOnlyException(toMailbox.getMailboxPath()));
        }
        //TODO lock the from mailbox too, in a non-deadlocking manner - how?
        return Mono.from(locker.executeReactiveWithLockReactive(toMailbox.getMailboxPath(),
            move(set, toMailbox, session)
                .map(map -> MessageRange.toRanges(new ArrayList<>(map.keySet()))),
            MailboxPathLocker.LockType.Write));
    }

    @Override
    public long getMessageCount(MailboxSession mailboxSession) throws MailboxException {
        return mapperFactory.getMessageMapper(mailboxSession).countMessagesInMailbox(getMailboxEntity());
    }

    @Override
    public MessageResultIterator getMessages(MessageRange set, FetchGroup fetchGroup, MailboxSession mailboxSession) {
        final MessageMapper messageMapper = mapperFactory.getMessageMapper(mailboxSession);
        return new StoreMessageResultIterator(messageMapper, mailbox, set, batchSizes, fetchGroup);
    }

    @Override
    public Publisher<MessageResult> getMessagesReactive(MessageRange set, FetchGroup fetchGroup, MailboxSession mailboxSession) {
        FetchType fetchType = FetchGroupConverter.getFetchType(fetchGroup);
        return Flux.from(mapperFactory.getMessageMapper(mailboxSession)
            .findInMailboxReactive(mailbox, set, fetchType, -1))
            .publishOn(forFetchType(fetchType))
            .map(Throwing.<MailboxMessage, MessageResult>function(message -> ResultUtils.loadMessageResult(message, fetchGroup)).sneakyThrow());
    }

    private Scheduler forFetchType(MessageMapper.FetchType fetchType) {
        if (fetchType == MessageMapper.FetchType.FULL) {
            return Schedulers.parallel();
        }
        return Schedulers.immediate();
    }

    @Override
    public Publisher<ComposedMessageIdWithMetaData> listMessagesMetadata(MessageRange set, MailboxSession session) {
        MessageMapper messageMapper = mapperFactory.getMessageMapper(session);
        return messageMapper.listMessagesMetadata(mailbox, set);
    }

    /**
     * Return a List which holds all uids of recent messages and optional reset
     * the recent flag on the messages for the uids
     */
    protected Mono<List<MessageUid>> recent(RecentMode recentMode, MailboxSession mailboxSession) throws MailboxException {
        MessageMapper messageMapper = mapperFactory.getMessageMapper(mailboxSession);

        switch (recentMode) {
            case IGNORE:
                return Mono.just(ImmutableList.of());
            case RETRIEVE:
                return messageMapper.findRecentMessageUidsInMailboxReactive(getMailboxEntity());
            case RESET:
                return resetRecents(messageMapper, mailboxSession);
            default:
                throw new RuntimeException("Unsupported recent mode " + recentMode);
        }
    }

    private Mono<List<MessageUid>> resetRecents(MessageMapper messageMapper, MailboxSession mailboxSession) throws MailboxException {
        if (!isWriteable(mailboxSession)) {
            throw new ReadOnlyException(getMailboxPath());
        }

        return messageMapper.resetRecentReactive(getMailboxEntity())
            .flatMap(updatedFlags -> publishResentFlagsUpdateIfNeeded(mailboxSession, updatedFlags)
                .thenReturn(updatedFlags.stream()
                    .map(UpdatedFlags::getUid)
                    .collect(ImmutableList.toImmutableList())));
    }

    private Mono<Void> publishResentFlagsUpdateIfNeeded(MailboxSession mailboxSession, List<UpdatedFlags> updatedFlags) {
        if (!updatedFlags.isEmpty()) {
            return eventBus.dispatch(EventFactory.flagsUpdated()
                    .randomEventId()
                    .mailboxSession(mailboxSession)
                    .mailbox(getMailboxEntity())
                    .updatedFlags(updatedFlags)
                    .build(),
                new MailboxIdRegistrationKey(mailbox.getMailboxId()));
        }
        return Mono.empty();
    }

    private Mono<Void> runPredeletionHooks(List<MessageUid> uids, MailboxSession session) {
        MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        Mono<DeleteOperation> deleteOperation = Flux.fromIterable(MessageRange.toRanges(uids))
            .flatMap(range -> messageMapper.findInMailboxReactive(mailbox, range, FetchType.METADATA, UNLIMITED), DEFAULT_CONCURRENCY)
            .map(mailboxMessage -> MetadataWithMailboxId.from(mailboxMessage.metaData(), mailboxMessage.getMailboxId()))
            .collect(ImmutableList.toImmutableList())
            .map(DeleteOperation::from);

        return deleteOperation.flatMap(preDeletionHooks::runHooks).then();
    }

    @Override
    public Flux<MessageUid> search(SearchQuery query, MailboxSession mailboxSession) throws MailboxException {
        if (query.equals(LIST_ALL_QUERY) || query.equals(LIST_FROM_ONE)) {
            return listAllMessageUids(mailboxSession);
        }
        return index.search(mailboxSession, getMailboxEntity(), query);
    }

    private Flux<MessageMetaData> copy(List<MailboxMessage> originalRows, MailboxSession session) {
        MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        try {
            QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(getMailboxPath());
            return Mono.from(quotaManager.getQuotasReactive(quotaRoot))
                .doOnNext(Throwing.<QuotaManager.Quotas>consumer(quotas -> new QuotaChecker(quotas, quotaRoot)
                    .tryAddition(originalRows.size(), originalRows.stream()
                        .mapToLong(MailboxMessage::getFullContentOctets)
                        .sum())).sneakyThrow())
                .thenMany(messageMapper.executeReactive(
                    messageMapper.copyReactive(getMailboxEntity(), originalRows))
                    .flatMapIterable(Function.identity()));
        } catch (MailboxException e) {
            return Flux.error(e);
        }
    }

    private Mono<MoveResult> move(List<MailboxMessage> originalRows, MailboxSession session) {
        List<MessageMetaData> originalRowsCopy = originalRows.stream()
            .map(MailboxMessage::metaData)
            .collect(ImmutableList.toImmutableList());
        MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        return messageMapper.executeReactive(messageMapper.moveReactive(getMailboxEntity(), originalRows))
            .map(data -> new MoveResult(data, originalRowsCopy));
    }


    private Mono<SortedMap<MessageUid, MessageMetaData>> copy(MessageRange set, StoreMessageManager to, MailboxSession session) {
        return retrieveOriginalRows(set, session)
            .collectList()
            .flatMap(originalRows -> to.copy(originalRows, session).collectList().flatMap(copyResult -> {
                SortedMap<MessageUid, MessageMetaData> copiedUids = collectMetadata(copyResult.iterator());

                ImmutableList<MessageId> messageIds = originalRows.stream()
                    .map(org.apache.james.mailbox.store.mail.model.Message::getMessageId)
                    .collect(ImmutableList.toImmutableList());

                MessageMoves messageMoves = MessageMoves.builder()
                    .previousMailboxIds(getMailboxEntity().getMailboxId())
                    .targetMailboxIds(to.getMailboxEntity().getMailboxId(), getMailboxEntity().getMailboxId())
                    .build();

                return Flux.concat(
                    eventBus.dispatch(EventFactory.added()
                            .randomEventId()
                            .mailboxSession(session)
                            .mailbox(to.getMailboxEntity())
                            .metaData(copiedUids)
                            .isDelivery(!IS_DELIVERY)
                            .isAppended(!IS_APPENDED)
                            .build(),
                        new MailboxIdRegistrationKey(to.getMailboxEntity().getMailboxId())),
                    eventBus.dispatch(EventFactory.moved()
                            .messageMoves(messageMoves)
                            .messageId(messageIds)
                            .session(session)
                            .build(),
                        messageMoves.impactedMailboxIds().map(MailboxIdRegistrationKey::new).collect(ImmutableSet.toImmutableSet())))
                    .then()
                    .thenReturn(copiedUids);
            }));
    }

    private Mono<SortedMap<MessageUid, MessageMetaData>> move(MessageRange set, StoreMessageManager to, MailboxSession session) {
        return retrieveOriginalRows(set, session)
            .collectList()
            .flatMap(originalRows -> to.move(originalRows, session).flatMap(moveResult -> {
                SortedMap<MessageUid, MessageMetaData> moveUids = collectMetadata(moveResult.getMovedMessages().iterator());

                ImmutableList<MessageId> messageIds = originalRows.stream()
                    .map(org.apache.james.mailbox.store.mail.model.Message::getMessageId)
                    .collect(ImmutableList.toImmutableList());

                MessageMoves messageMoves = MessageMoves.builder()
                    .previousMailboxIds(getMailboxEntity().getMailboxId())
                    .targetMailboxIds(to.getMailboxEntity().getMailboxId())
                    .build();

                return Flux.concat(
                    eventBus.dispatch(EventFactory.added()
                            .randomEventId()
                            .mailboxSession(session)
                            .mailbox(to.getMailboxEntity())
                            .metaData(moveUids)
                            .isDelivery(!IS_DELIVERY)
                            .isAppended(!IS_APPENDED)
                            .movedFrom(getId())
                            .build(),
                        new MailboxIdRegistrationKey(to.getMailboxEntity().getMailboxId())),
                    eventBus.dispatch(EventFactory.expunged()
                            .randomEventId()
                            .mailboxSession(session)
                            .mailbox(getMailboxEntity())
                            .addMetaData(moveResult.getOriginalMessages())
                            .movedTo(to.getId())
                            .build(),
                        new MailboxIdRegistrationKey(mailbox.getMailboxId())),
                    eventBus.dispatch(EventFactory.moved()
                            .messageMoves(messageMoves)
                            .messageId(messageIds)
                            .session(session)
                            .build(),
                        messageMoves.impactedMailboxIds().map(MailboxIdRegistrationKey::new).collect(ImmutableSet.toImmutableSet())))
                    .then()
                    .thenReturn(moveUids);
            }));
    }

    private Flux<MailboxMessage> retrieveOriginalRows(MessageRange set, MailboxSession session) {
        MessageMapper messageMapper = mapperFactory.getMessageMapper(session);
        return messageMapper.findInMailboxReactive(mailbox, set, FetchType.METADATA, UNLIMITED);
    }

    private SortedMap<MessageUid, MessageMetaData> collectMetadata(Iterator<MessageMetaData> ids) {
        final SortedMap<MessageUid, MessageMetaData> copiedMessages = new TreeMap<>();
        while (ids.hasNext()) {
            MessageMetaData data = ids.next();
            copiedMessages.put(data.getUid(), data);
        }
        return copiedMessages;
    }

    @Override
    public MailboxId getId() {
        return mailbox.getMailboxId();
    }
    
    @Override
    public MailboxPath getMailboxPath() {
        return getMailboxEntity().generateAssociatedPath();
    }

    @Override
    public Flags getApplicableFlags(MailboxSession session) throws MailboxException {
        return mapperFactory.getMessageMapper(session)
            .getApplicableFlag(mailbox);
    }

    @Override
    public Mono<Flags> getApplicableFlagsReactive(MailboxSession session) {
        return mapperFactory.getMessageMapper(session)
            .getApplicableFlagReactive(mailbox);
    }

    private Flux<MessageUid> listAllMessageUids(MailboxSession session) throws MailboxException {
        final MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        return messageMapper.execute(
            () -> messageMapper.listAllMessageUids(mailbox));
    }

    @Override
    public EnumSet<MessageCapabilities> getSupportedMessageCapabilities() {
        return messageCapabilities;
    }
}
