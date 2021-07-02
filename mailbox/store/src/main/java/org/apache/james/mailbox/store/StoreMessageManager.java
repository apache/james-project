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

import static org.apache.james.mailbox.extension.PreDeletionHook.DeleteOperation;
import static org.apache.james.mailbox.store.mail.AbstractMessageMapper.UNLIMITED;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManager.MessageCapabilities;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
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
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.quota.QuotaChecker;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.streaming.CountingInputStream;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultBodyDescriptorBuilder;
import org.apache.james.mime4j.message.HeaderImpl;
import org.apache.james.mime4j.message.MaximalBodyDescriptor;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.apache.james.mime4j.stream.RecursionMode;
import org.apache.james.util.IteratorWrapper;
import org.apache.james.util.io.BodyOffsetInputStream;
import org.apache.james.util.io.InputStreamConsummer;
import org.apache.james.util.streams.Iterators;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
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

    public Mailbox getMailboxEntity() throws MailboxException {
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
    protected Flags getPermanentFlags(MailboxSession session) {

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
        return MailboxCounters.builder()
            .mailboxId(mailbox.getMailboxId())
            .count(0)
            .unseen(0)
            .build();
    }


    @Override
    public Publisher<MailboxCounters> getMailboxCountersReactive(MailboxSession mailboxSession) {
        if (storeRightManager.hasRight(mailbox, MailboxACL.Right.Read, mailboxSession)) {
            return mapperFactory.createMessageMapper(mailboxSession).getMailboxCountersReactive(mailbox);
        }
        return Mono.just(MailboxCounters.builder()
            .mailboxId(mailbox.getMailboxId())
            .count(0)
            .unseen(0)
            .build());
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

    /**
     * Return true. If an subclass don't want to store mod-sequences in a
     * permanent way just override this and return false
     * 
     * @return true
     */
    @Override
    public boolean isModSeqPermanent(MailboxSession session) {
        return true;
    }

    @Override
    public Iterator<MessageUid> expunge(MessageRange set, MailboxSession mailboxSession) throws MailboxException {
        if (!isWriteable(mailboxSession)) {
            throw new ReadOnlyException(getMailboxPath());
        }

        List<MessageUid> uids = retrieveMessagesMarkedForDeletion(set, mailboxSession);
        Map<MessageUid, MessageMetaData> deletedMessages = deleteMessages(uids, mailboxSession);

        dispatchExpungeEvent(mailboxSession, deletedMessages);
        return deletedMessages.keySet().iterator();
    }

    private List<MessageUid> retrieveMessagesMarkedForDeletion(MessageRange messageRange, MailboxSession session) throws MailboxException {
        MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        return messageMapper.execute(
            () -> messageMapper.retrieveMessagesMarkedForDeletion(getMailboxEntity(), messageRange));
    }

    @Override
    public void delete(List<MessageUid> messageUids, MailboxSession mailboxSession) throws MailboxException {
        Map<MessageUid, MessageMetaData> deletedMessages = deleteMessages(messageUids, mailboxSession);

        dispatchExpungeEvent(mailboxSession, deletedMessages);
    }

    private Map<MessageUid, MessageMetaData> deleteMessages(List<MessageUid> messageUids, MailboxSession session) throws MailboxException {
        if (messageUids.isEmpty()) {
            return ImmutableMap.of();
        }

        MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        runPredeletionHooks(messageUids, session);

        return messageMapper.execute(
            () -> messageMapper.deleteMessages(getMailboxEntity(), messageUids));
    }

    private void dispatchExpungeEvent(MailboxSession mailboxSession, Map<MessageUid, MessageMetaData> deletedMessages) throws MailboxException {
        eventBus.dispatch(EventFactory.expunged()
                .randomEventId()
                .mailboxSession(mailboxSession)
                .mailbox(getMailboxEntity())
                .metaData(ImmutableSortedMap.copyOf(deletedMessages))
                .build(),
            new MailboxIdRegistrationKey(mailbox.getMailboxId()))
            .subscribeOn(Schedulers.elastic())
            .block();
    }

    @Override
    public AppendResult appendMessage(AppendCommand appendCommand, MailboxSession session) throws MailboxException {
        return MailboxReactorUtils.block(appendMessage(
            appendCommand.getMsgIn(),
            appendCommand.getInternalDate(),
            session,
            appendCommand.isRecent(),
            appendCommand.getFlags(),
            appendCommand.getMaybeParsedMessage()));
    }

    @Override
    public Publisher<AppendResult> appendMessageReactive(AppendCommand appendCommand, MailboxSession session) {
        return appendMessage(
            appendCommand.getMsgIn(),
            appendCommand.getInternalDate(),
            session,
            appendCommand.isRecent(),
            appendCommand.getFlags(),
            appendCommand.getMaybeParsedMessage());
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
            file = File.createTempFile("imap", ".msg");
            try (FileOutputStream out = new FileOutputStream(file);
                BufferedOutputStream bufferedOut = new BufferedOutputStream(out);
                BufferedInputStream tmpMsgIn = new BufferedInputStream(new TeeInputStream(msgIn, bufferedOut));
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
                    getFlags(mailboxSession, isRecent, flagsToBeSet), bodyStartOctet, unparsedMimeMessqage, headers));
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

    private Mono<AppendResult> appendMessage(Content msgIn, Date internalDate, final MailboxSession mailboxSession, boolean isRecent, Flags flagsToBeSet, Optional<Message> maybeMessage) {
        return Mono.fromCallable(() -> {
            if (!isWriteable(mailboxSession)) {
                throw new ReadOnlyException(getMailboxPath());
            }

            try (InputStream contentStream = msgIn.getInputStream();
                    BufferedInputStream bufferedContentStream = new BufferedInputStream(contentStream);
                    BodyOffsetInputStream bIn = new BodyOffsetInputStream(bufferedContentStream)) {
                Pair<PropertyBuilder, HeaderImpl> pair = parseProperties(bIn);
                PropertyBuilder propertyBuilder = pair.getLeft();
                HeaderImpl headers = pair.getRight();
                int bodyStartOctet = getBodyStartOctet(bIn);

                return createAndDispatchMessage(computeInternalDate(internalDate),
                    mailboxSession, msgIn, propertyBuilder,
                    getFlags(mailboxSession, isRecent, flagsToBeSet), bodyStartOctet, maybeMessage, headers);
            } catch (IOException | MimeException e) {
                throw new MailboxException("Unable to parse message", e);
            }
        }).flatMap(Function.identity())
            .subscribeOn(Schedulers.elastic());
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
        final MimeTokenStream parser = new MimeTokenStream(MimeConfig.PERMISSIVE, new DefaultBodyDescriptorBuilder());

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

    private Mono<AppendResult> createAndDispatchMessage(Date internalDate, MailboxSession mailboxSession, Content content, PropertyBuilder propertyBuilder, Flags flags, int bodyStartOctet, Optional<Message> maybeMessage, HeaderImpl headers) throws MailboxException {
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
    public boolean isWriteable(MailboxSession session) throws MailboxException {
        return storeRightManager.isReadWrite(session, mailbox, getSharedPermanentFlags(session));
    }

    @Override
    public MailboxMetaData getMetaData(boolean resetRecent, MailboxSession mailboxSession, MailboxMetaData.FetchGroup fetchGroup) throws MailboxException {
        MailboxACL resolvedAcl = getResolvedAcl(mailboxSession);
        boolean hasReadRight = storeRightManager.hasRight(mailbox, MailboxACL.Right.Read, mailboxSession);
        if (!hasReadRight) {
            return MailboxMetaData.sensibleInformationFree(resolvedAcl, getMailboxEntity().getUidValidity(), isWriteable(mailboxSession), isModSeqPermanent(mailboxSession));
        }
        List<MessageUid> recent;
        Flags permanentFlags = getPermanentFlags(mailboxSession);
        UidValidity uidValidity = getMailboxEntity().getUidValidity();
        MessageUid uidNext = mapperFactory.getMessageMapper(mailboxSession).getLastUid(mailbox)
                .map(MessageUid::next)
                .orElse(MessageUid.MIN_VALUE);
        ModSeq highestModSeq = mapperFactory.getMessageMapper(mailboxSession).getHighestModSeq(mailbox);
        long messageCount;
        long unseenCount;
        MessageUid firstUnseen;
        switch (fetchGroup) {
        case UNSEEN_COUNT:
            MailboxCounters mailboxCounters = getMailboxCounters(mailboxSession);
            unseenCount = mailboxCounters.getUnseen();
            messageCount = mailboxCounters.getCount();
            firstUnseen = null;
            recent = recent(resetRecent, mailboxSession);

            break;
        case FIRST_UNSEEN:
            firstUnseen = findFirstUnseenMessageUid(mailboxSession);
            messageCount = getMessageCount(mailboxSession);
            unseenCount = 0;
            recent = recent(resetRecent, mailboxSession);

            break;
        case NO_UNSEEN:
            firstUnseen = null;
            unseenCount = 0;
            messageCount = getMessageCount(mailboxSession);
            recent = recent(resetRecent, mailboxSession);

            break;
        default:
            firstUnseen = null;
            unseenCount = 0;
            messageCount = -1;
            // just reset the recent but not include them in the metadata
            if (resetRecent) {
                recent(resetRecent, mailboxSession);
            }
            recent = new ArrayList<>();
            break;
        }
        return new MailboxMetaData(recent, permanentFlags, uidValidity, uidNext, highestModSeq, messageCount, unseenCount, firstUnseen, isWriteable(mailboxSession), isModSeqPermanent(mailboxSession), resolvedAcl);
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
        List<UpdatedFlags> updatedFlags = Iterators.toStream(it).collect(Guavate.toImmutableList());

        eventBus.dispatch(EventFactory.flagsUpdated()
                .randomEventId()
                .mailboxSession(mailboxSession)
                .mailbox(getMailboxEntity())
                .updatedFlags(updatedFlags)
                .build(),
                new MailboxIdRegistrationKey(mailbox.getMailboxId()))
            .subscribeOn(Schedulers.elastic())
            .block();

        return updatedFlags.stream().collect(Guavate.toImmutableMap(
            UpdatedFlags::getUid,
            UpdatedFlags::getNewFlags));
    }

    /**
     * Copy the {@link MessageRange} to the {@link StoreMessageManager}
     */
    public List<MessageRange> copyTo(final MessageRange set, final StoreMessageManager toMailbox, final MailboxSession session) throws MailboxException {
        if (!toMailbox.isWriteable(session)) {
            throw new ReadOnlyException(toMailbox.getMailboxPath());
        }

        return locker.executeWithLock(toMailbox.getMailboxPath(), () -> {
            SortedMap<MessageUid, MessageMetaData> copiedUids = copy(set, toMailbox, session);
            return MessageRange.toRanges(new ArrayList<>(copiedUids.keySet()));
        }, MailboxPathLocker.LockType.Write);
    }

    /**
     * Move the {@link MessageRange} to the {@link StoreMessageManager}
     */
    public List<MessageRange> moveTo(final MessageRange set, final StoreMessageManager toMailbox, final MailboxSession session) throws MailboxException {
        if (!isWriteable(session)) {
            throw new ReadOnlyException(getMailboxPath());
        }
        if (!toMailbox.isWriteable(session)) {
            throw new ReadOnlyException(toMailbox.getMailboxPath());
        }

        //TODO lock the from mailbox too, in a non-deadlocking manner - how?
        return locker.executeWithLock(toMailbox.getMailboxPath(), () -> {
            SortedMap<MessageUid, MessageMetaData> movedUids = move(set, toMailbox, session);
            return MessageRange.toRanges(new ArrayList<>(movedUids.keySet()));
        }, MailboxPathLocker.LockType.Write);
    }

    @Override
    public long getMessageCount(MailboxSession mailboxSession) throws MailboxException {
        return mapperFactory.getMessageMapper(mailboxSession).countMessagesInMailbox(getMailboxEntity());
    }

    @Override
    public MessageResultIterator getMessages(MessageRange set, FetchGroup fetchGroup, MailboxSession mailboxSession) throws MailboxException {
        final MessageMapper messageMapper = mapperFactory.getMessageMapper(mailboxSession);
        return new StoreMessageResultIterator(messageMapper, mailbox, set, batchSizes, fetchGroup);
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
    protected List<MessageUid> recent(final boolean reset, MailboxSession mailboxSession) throws MailboxException {
        MessageMapper messageMapper = mapperFactory.getMessageMapper(mailboxSession);

        return messageMapper.execute(() -> {
            if (reset) {
                return resetRecents(messageMapper, mailboxSession);
            }
            return messageMapper.findRecentMessageUidsInMailbox(getMailboxEntity());
        });

    }

    private List<MessageUid> resetRecents(MessageMapper messageMapper, MailboxSession mailboxSession) throws MailboxException {
        if (!isWriteable(mailboxSession)) {
            throw new ReadOnlyException(getMailboxPath());
        }

        List<UpdatedFlags> updatedFlags = messageMapper.resetRecent(getMailboxEntity());

        eventBus.dispatch(EventFactory.flagsUpdated()
                .randomEventId()
                .mailboxSession(mailboxSession)
                .mailbox(getMailboxEntity())
                .updatedFlags(updatedFlags)
                .build(),
            new MailboxIdRegistrationKey(mailbox.getMailboxId()))
            .subscribeOn(Schedulers.elastic())
            .block();

        return updatedFlags.stream()
            .map(UpdatedFlags::getUid)
            .collect(Guavate.toImmutableList());
    }

    private void runPredeletionHooks(List<MessageUid> uids, MailboxSession session) {
        MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        Mono<DeleteOperation> deleteOperation = Flux.fromIterable(MessageRange.toRanges(uids))
            .publishOn(Schedulers.elastic())
            .flatMap(range -> messageMapper.findInMailboxReactive(mailbox, range, FetchType.Metadata, UNLIMITED), DEFAULT_CONCURRENCY)
            .map(mailboxMessage -> MetadataWithMailboxId.from(mailboxMessage.metaData(), mailboxMessage.getMailboxId()))
            .collect(Guavate.toImmutableList())
            .map(DeleteOperation::from);

        deleteOperation.flatMap(preDeletionHooks::runHooks).block();
    }

    @Override
    public Flux<MessageUid> search(SearchQuery query, MailboxSession mailboxSession) throws MailboxException {
        if (query.equals(LIST_ALL_QUERY) || query.equals(LIST_FROM_ONE)) {
            return listAllMessageUids(mailboxSession);
        }
        return index.search(mailboxSession, getMailboxEntity(), query);
    }

    private Iterator<MessageMetaData> copy(Iterator<MailboxMessage> originalRows, MailboxSession session) throws MailboxException {
        int batchSize = batchSizes.getCopyBatchSize().orElse(1);
        final List<MessageMetaData> copiedRows = new ArrayList<>();
        final MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        Iterator<List<MailboxMessage>> groupedOriginalRows = com.google.common.collect.Iterators.partition(originalRows, batchSize);

        while (originalRows.hasNext()) {
            List<MailboxMessage> originalMessages = groupedOriginalRows.next();

            new QuotaChecker(quotaManager, quotaRootResolver, mailbox)
                .tryAddition(originalMessages.size(), originalMessages.stream()
                    .mapToLong(MailboxMessage::getFullContentOctets)
                    .sum());
            List<MessageMetaData> data = messageMapper.execute(
                () -> messageMapper.copy(getMailboxEntity(), originalMessages));
            copiedRows.addAll(data);
        }
        return copiedRows.iterator();
    }

    private MoveResult move(Iterator<MailboxMessage> originalRows, MailboxSession session) throws MailboxException {
        int batchSize = batchSizes.getMoveBatchSize().orElse(1);
        final List<MessageMetaData> movedRows = new ArrayList<>();
        final List<MessageMetaData> originalRowsCopy = new ArrayList<>();
        final MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        Iterator<List<MailboxMessage>> groupedOriginalRows = com.google.common.collect.Iterators.partition(originalRows, batchSize);

        while (groupedOriginalRows.hasNext()) {
            List<MailboxMessage> originalMessages = groupedOriginalRows.next();
            originalRowsCopy.addAll(originalMessages.stream()
                .map(MailboxMessage::metaData)
                .collect(Guavate.toImmutableList()));
            List<MessageMetaData> data = messageMapper.execute(
                () -> messageMapper.move(getMailboxEntity(), originalMessages));
            movedRows.addAll(data);
        }
        return new MoveResult(movedRows.iterator(), originalRowsCopy.iterator());
    }


    private SortedMap<MessageUid, MessageMetaData> copy(MessageRange set, StoreMessageManager to, MailboxSession session) throws MailboxException {
        IteratorWrapper<MailboxMessage> originalRows = new IteratorWrapper<>(retrieveOriginalRows(set, session));

        SortedMap<MessageUid, MessageMetaData> copiedUids = collectMetadata(to.copy(originalRows, session));

        ImmutableList.Builder<MessageId> messageIds = ImmutableList.builder();
        for (MailboxMessage message : originalRows.getEntriesSeen()) {
            messageIds.add(message.getMessageId());
        }

        MessageMoves messageMoves = MessageMoves.builder()
            .previousMailboxIds(getMailboxEntity().getMailboxId())
            .targetMailboxIds(to.getMailboxEntity().getMailboxId(), getMailboxEntity().getMailboxId())
            .build();
        Flux.concat(
            eventBus.dispatch(EventFactory.added()
                    .randomEventId()
                    .mailboxSession(session)
                    .mailbox(to.getMailboxEntity())
                    .metaData(copiedUids)
                    .build(),
                new MailboxIdRegistrationKey(to.getMailboxEntity().getMailboxId())),
            eventBus.dispatch(EventFactory.moved()
                    .session(session)
                    .messageMoves(messageMoves)
                    .messageId(messageIds.build())
                    .build(),
                messageMoves.impactedMailboxIds().map(MailboxIdRegistrationKey::new).collect(Guavate.toImmutableSet())))
            .subscribeOn(Schedulers.elastic())
            .blockLast();

        return copiedUids;
    }

    private SortedMap<MessageUid, MessageMetaData> move(MessageRange set, StoreMessageManager to, MailboxSession session) throws MailboxException {
        IteratorWrapper<MailboxMessage> originalRows = new IteratorWrapper<>(retrieveOriginalRows(set, session));

        MoveResult moveResult = to.move(originalRows, session);
        SortedMap<MessageUid, MessageMetaData> moveUids = collectMetadata(moveResult.getMovedMessages());

        ImmutableList.Builder<MessageId> messageIds = ImmutableList.builder();
        for (MailboxMessage message : originalRows.getEntriesSeen()) {
            messageIds.add(message.getMessageId());
        }

        MessageMoves messageMoves = MessageMoves.builder()
            .previousMailboxIds(getMailboxEntity().getMailboxId())
            .targetMailboxIds(to.getMailboxEntity().getMailboxId())
            .build();
        Flux.concat(
            eventBus.dispatch(EventFactory.added()
                    .randomEventId()
                    .mailboxSession(session)
                    .mailbox(to.getMailboxEntity())
                    .metaData(moveUids)
                    .build(),
                new MailboxIdRegistrationKey(to.getMailboxEntity().getMailboxId())),
            eventBus.dispatch(EventFactory.expunged()
                    .randomEventId()
                    .mailboxSession(session)
                    .mailbox(getMailboxEntity())
                    .addMetaData(moveResult.getOriginalMessages())
                    .build(),
                new MailboxIdRegistrationKey(mailbox.getMailboxId())),
            eventBus.dispatch(EventFactory.moved()
                    .messageMoves(messageMoves)
                    .messageId(messageIds.build())
                    .session(session)
                    .build(),
                messageMoves.impactedMailboxIds().map(MailboxIdRegistrationKey::new).collect(Guavate.toImmutableSet())))
            .subscribeOn(Schedulers.elastic())
            .blockLast();

        return moveUids;
    }

    private Iterator<MailboxMessage> retrieveOriginalRows(MessageRange set, MailboxSession session) throws MailboxException {
        MessageMapper messageMapper = mapperFactory.getMessageMapper(session);
        return messageMapper.findInMailbox(mailbox, set, FetchType.Metadata, UNLIMITED);
    }

    private SortedMap<MessageUid, MessageMetaData> collectMetadata(Iterator<MessageMetaData> ids) {
        final SortedMap<MessageUid, MessageMetaData> copiedMessages = new TreeMap<>();
        while (ids.hasNext()) {
            MessageMetaData data = ids.next();
            copiedMessages.put(data.getUid(), data);
        }
        return copiedMessages;
    }

    /**
     * Return the uid of the first unseen message or null of none is found
     */
    protected MessageUid findFirstUnseenMessageUid(MailboxSession session) throws MailboxException {
        MessageMapper messageMapper = mapperFactory.getMessageMapper(session);
        return messageMapper.findFirstUnseenMessageUid(getMailboxEntity());
    }

    @Override
    public MailboxId getId() {
        return mailbox.getMailboxId();
    }
    
    @Override
    public MailboxPath getMailboxPath() throws MailboxException {
        return getMailboxEntity().generateAssociatedPath();
    }

    @Override
    public Flags getApplicableFlags(MailboxSession session) throws MailboxException {
        return mapperFactory.getMessageMapper(session)
            .getApplicableFlag(mailbox);
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
