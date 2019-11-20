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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.internet.SharedInputStream;
import javax.mail.util.SharedFileInputStream;

import org.apache.commons.io.input.TeeInputStream;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManager.MessageCapabilities;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.ReadOnlyException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageId.Factory;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult.FetchGroup;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.quota.QuotaChecker;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.streaming.CountingInputStream;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.message.DefaultBodyDescriptorBuilder;
import org.apache.james.mime4j.message.HeaderImpl;
import org.apache.james.mime4j.message.MaximalBodyDescriptor;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.apache.james.mime4j.stream.RecursionMode;
import org.apache.james.util.BodyOffsetInputStream;
import org.apache.james.util.IteratorWrapper;
import org.apache.james.util.streams.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * {@link MailboxListener} and so help with handling concurrent
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

    private static final Logger LOG = LoggerFactory.getLogger(StoreMessageManager.class);

    private final EnumSet<MailboxManager.MessageCapabilities> messageCapabilities;
    private final EventBus eventBus;
    private final Mailbox mailbox;
    private final MailboxSessionMapperFactory mapperFactory;
    private final MessageSearchIndex index;
    private final StoreRightManager storeRightManager;
    private final QuotaManager quotaManager;
    private final QuotaRootResolver quotaRootResolver;
    private final MailboxPathLocker locker;
    private final MessageParser messageParser;
    private final Factory messageIdFactory;
    private final BatchSizes batchSizes;
    private final PreDeletionHooks preDeletionHooks;

    public StoreMessageManager(EnumSet<MailboxManager.MessageCapabilities> messageCapabilities, MailboxSessionMapperFactory mapperFactory,
                               MessageSearchIndex index, EventBus eventBus,
                               MailboxPathLocker locker, Mailbox mailbox,
                               QuotaManager quotaManager, QuotaRootResolver quotaRootResolver, MessageParser messageParser, MessageId.Factory messageIdFactory, BatchSizes batchSizes,
                               StoreRightManager storeRightManager, PreDeletionHooks preDeletionHooks) {
        this.messageCapabilities = messageCapabilities;
        this.eventBus = eventBus;
        this.mailbox = mailbox;
        this.mapperFactory = mapperFactory;
        this.index = index;
        this.locker = locker;
        this.quotaManager = quotaManager;
        this.quotaRootResolver = quotaRootResolver;
        this.messageParser = messageParser;
        this.messageIdFactory = messageIdFactory;
        this.batchSizes = batchSizes;
        this.storeRightManager = storeRightManager;
        this.preDeletionHooks = preDeletionHooks;
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
            .unseen(0)
            .count(0)
            .build();
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
            .block();
    }

    @Override
    public ComposedMessageId appendMessage(AppendCommand appendCommand, MailboxSession session) throws MailboxException {
        return appendMessage(
            appendCommand.getMsgIn(),
            appendCommand.getInternalDate(),
            session,
            appendCommand.isRecent(),
            appendCommand.getFlags());
    }

    @Override
    public ComposedMessageId appendMessage(InputStream msgIn, Date internalDate, final MailboxSession mailboxSession, boolean isRecent, Flags flagsToBeSet) throws MailboxException {
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
                // Disable line length... This should be handled by the smtp server
                // component and not the parser itself
                // https://issues.apache.org/jira/browse/IMAP-122
                final MimeTokenStream parser = getParser(bIn);
                readHeader(parser);
                final MaximalBodyDescriptor descriptor = (MaximalBodyDescriptor) parser.getBodyDescriptor();
                final MediaType mediaType = getMediaType(descriptor);
                final PropertyBuilder propertyBuilder = getPropertyBuilder(descriptor, mediaType.mediaType, mediaType.subType);
                setTextualLinesCount(parser, mediaType.mediaType, propertyBuilder);
                final Flags flags = getFlags(mailboxSession, isRecent, flagsToBeSet);

                if (internalDate == null) {
                    internalDate = new Date();
                }
                consumeStream(bufferedOut, tmpMsgIn);
                int bodyStartOctet = getBodyStartOctet(bIn);
                return createAndDispatchMessage(internalDate, mailboxSession, file, propertyBuilder, flags, bodyStartOctet);
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

    private void consumeStream(BufferedOutputStream bufferedOut, BufferedInputStream tmpMsgIn) throws IOException {
        byte[] discard = new byte[4096];
        while (tmpMsgIn.read(discard) != -1) {
            // consume the rest of the stream so everything get copied to
            // the file now
            // via the TeeInputStream
        }
        bufferedOut.flush();
    }

    private int getBodyStartOctet(BodyOffsetInputStream bIn) {
        int bodyStartOctet = (int) bIn.getBodyStartOffset();
        if (bodyStartOctet == -1) {
            bodyStartOctet = 0;
        }
        return bodyStartOctet;
    }

    private ComposedMessageId createAndDispatchMessage(Date internalDate, MailboxSession mailboxSession, File file, PropertyBuilder propertyBuilder, Flags flags, int bodyStartOctet) throws IOException, MailboxException {
        try (SharedFileInputStream contentIn = new SharedFileInputStream(file)) {
            final int size = (int) file.length();

            final List<MessageAttachment> attachments = extractAttachments(contentIn);
            propertyBuilder.setHasAttachment(hasNonInlinedAttachment(attachments));

            final MailboxMessage message = createMessage(internalDate, size, bodyStartOctet, contentIn, flags, propertyBuilder, attachments);

            new QuotaChecker(quotaManager, quotaRootResolver, mailbox).tryAddition(1, size);

            return locker.executeWithLock(getMailboxPath(), () -> {
                MessageMetaData data = appendMessageToStore(message, attachments, mailboxSession);

                Mailbox mailbox = getMailboxEntity();

                eventBus.dispatch(EventFactory.added()
                    .randomEventId()
                    .mailboxSession(mailboxSession)
                    .mailbox(mailbox)
                    .addMetaData(message.metaData())
                    .build(),
                    new MailboxIdRegistrationKey(mailbox.getMailboxId()))
                    .block();
                return new ComposedMessageId(mailbox.getMailboxId(), data.getMessageId(), data.getUid());
            }, MailboxPathLocker.LockType.Write);
        }
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
        final String boundary = descriptor.getBoundary();
        if (boundary != null) {
            propertyBuilder.setBoundary(boundary);
        }
        return propertyBuilder;
    }

    private boolean hasNonInlinedAttachment(List<MessageAttachment> attachments) {
        return attachments.stream()
            .anyMatch(messageAttachment -> !messageAttachment.isInlinedWithCid());
    }

    private List<MessageAttachment> extractAttachments(SharedFileInputStream contentIn) {
        try {
            return messageParser.retrieveAttachments(contentIn);
        } catch (Exception e) {
            LOG.warn("Error while parsing mail's attachments: {}", e.getMessage(), e);
            return ImmutableList.of();
        }
    }

    /**
     * Create a new {@link MailboxMessage} for the given data
     */
    protected MailboxMessage createMessage(Date internalDate, int size, int bodyStartOctet, SharedInputStream content, Flags flags, PropertyBuilder propertyBuilder, List<MessageAttachment> attachments) throws MailboxException {
        return new SimpleMailboxMessage(messageIdFactory.generate(), internalDate, size, bodyStartOctet, content, flags, propertyBuilder, getMailboxEntity().getMailboxId(), attachments);
    }

    @Override
    public boolean isWriteable(MailboxSession session) throws MailboxException {
        return storeRightManager.isReadWrite(session, mailbox, getSharedPermanentFlags(session));
    }

    @Override
    public MetaData getMetaData(boolean resetRecent, MailboxSession mailboxSession, MetaData.FetchGroup fetchGroup) throws MailboxException {
        MailboxACL resolvedAcl = getResolvedAcl(mailboxSession);
        boolean hasReadRight = storeRightManager.hasRight(mailbox, MailboxACL.Right.Read, mailboxSession);
        if (!hasReadRight) {
            return MailboxMetaData.sensibleInformationFree(resolvedAcl, getMailboxEntity().getUidValidity(), isWriteable(mailboxSession), isModSeqPermanent(mailboxSession));
        }
        List<MessageUid> recent;
        Flags permanentFlags = getPermanentFlags(mailboxSession);
        long uidValidity = getMailboxEntity().getUidValidity();
        MessageUid uidNext = mapperFactory.getMessageMapper(mailboxSession).getLastUid(mailbox)
                .map(MessageUid::next)
                .orElse(MessageUid.MIN_VALUE);
        long highestModSeq = mapperFactory.getMessageMapper(mailboxSession).getHighestModSeq(mailbox);
        long messageCount;
        long unseenCount;
        MessageUid firstUnseen;
        switch (fetchGroup) {
        case UNSEEN_COUNT:
            unseenCount = countUnseenMessagesInMailbox(mailboxSession);
            messageCount = getMessageCount(mailboxSession);
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

    protected MessageMetaData appendMessageToStore(final MailboxMessage message, final List<MessageAttachment> messageAttachments, MailboxSession session) throws MailboxException {
        final MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        return mapperFactory.getMessageMapper(session).execute(() -> {
            storeAttachment(message, messageAttachments, session);
            return messageMapper.add(getMailboxEntity(), message);
        });
    }

    protected void storeAttachment(final MailboxMessage message, final List<MessageAttachment> messageAttachments, final MailboxSession session) throws MailboxException {

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

    /**
     * Return a List which holds all uids of recent messages and optional reset
     * the recent flag on the messages for the uids
     */
    protected List<MessageUid> recent(final boolean reset, MailboxSession mailboxSession) throws MailboxException {
        if (reset) {
            if (!isWriteable(mailboxSession)) {
                throw new ReadOnlyException(getMailboxPath());
            }
        }
        final MessageMapper messageMapper = mapperFactory.getMessageMapper(mailboxSession);

        return messageMapper.execute(() -> {
            final List<MessageUid> members = messageMapper.findRecentMessageUidsInMailbox(getMailboxEntity());

            // Convert to MessageRanges so we may be able to optimize the
            // flag update
            List<MessageRange> ranges = MessageRange.toRanges(members);
            for (MessageRange range : ranges) {
                if (reset) {
                    // only call save if we need to
                    messageMapper.updateFlags(getMailboxEntity(), new FlagsUpdateCalculator(new Flags(Flag.RECENT), FlagsUpdateMode.REMOVE), range);
                }
            }
            return members;
        });

    }

    private void runPredeletionHooks(List<MessageUid> uids, MailboxSession session) throws MailboxException {
        MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        DeleteOperation deleteOperation = Flux.fromIterable(MessageRange.toRanges(uids))
            .publishOn(Schedulers.boundedElastic())
            .flatMap(range -> Mono.fromCallable(() -> messageMapper.findInMailbox(mailbox, range, FetchType.Metadata, UNLIMITED))
                .flatMapMany(iterator -> Flux.fromStream(Iterators.toStream(iterator))))
            .map(mailboxMessage -> MetadataWithMailboxId.from(mailboxMessage.metaData(), mailboxMessage.getMailboxId()))
            .collect(Guavate.toImmutableList())
            .map(DeleteOperation::from)
            .block();

        preDeletionHooks.runHooks(deleteOperation).block();
    }

    @Override
    public Stream<MessageUid> search(SearchQuery query, MailboxSession mailboxSession) throws MailboxException {
        if (query.equals(new SearchQuery(SearchQuery.all()))) {
            return listAllMessageUids(mailboxSession);
        }
        return index.search(mailboxSession, getMailboxEntity(), query);
    }

    private Iterator<MessageMetaData> copy(Iterator<MailboxMessage> originalRows, MailboxSession session) throws MailboxException {
        final List<MessageMetaData> copiedRows = new ArrayList<>();
        final MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        while (originalRows.hasNext()) {
            final MailboxMessage originalMessage = originalRows.next();
            new QuotaChecker(quotaManager, quotaRootResolver, mailbox)
                .tryAddition(1, originalMessage.getFullContentOctets());
            MessageMetaData data = messageMapper.execute(
                () -> messageMapper.copy(getMailboxEntity(), originalMessage));
            copiedRows.add(data);
        }
        return copiedRows.iterator();
    }

    private MoveResult move(Iterator<MailboxMessage> originalRows, MailboxSession session) throws MailboxException {
        final List<MessageMetaData> movedRows = new ArrayList<>();
        final List<MessageMetaData> originalRowsCopy = new ArrayList<>();
        final MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        while (originalRows.hasNext()) {
            final MailboxMessage originalMessage = originalRows.next();
            originalRowsCopy.add(originalMessage.metaData());
            MessageMetaData data = messageMapper.execute(
                () -> messageMapper.move(getMailboxEntity(), originalMessage));
            movedRows.add(data);
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
            .blockLast();

        return moveUids;
    }

    private Iterator<MailboxMessage> retrieveOriginalRows(MessageRange set, MailboxSession session) throws MailboxException {
        MessageMapper messageMapper = mapperFactory.getMessageMapper(session);
        return messageMapper.findInMailbox(mailbox, set, FetchType.Full, UNLIMITED);
    }

    private SortedMap<MessageUid, MessageMetaData> collectMetadata(Iterator<MessageMetaData> ids) {
        final SortedMap<MessageUid, MessageMetaData> copiedMessages = new TreeMap<>();
        while (ids.hasNext()) {
            MessageMetaData data = ids.next();
            copiedMessages.put(data.getUid(), data);
        }
        return copiedMessages;
    }

    protected long countUnseenMessagesInMailbox(MailboxSession session) throws MailboxException {
        MessageMapper messageMapper = mapperFactory.getMessageMapper(session);
        return messageMapper.countUnseenMessagesInMailbox(getMailboxEntity());
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

    private Stream<MessageUid> listAllMessageUids(MailboxSession session) throws MailboxException {
        final MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

        return messageMapper.execute(
            () -> Iterators.toStream(messageMapper.listAllMessageUids(mailbox)));
    }

    @Override
    public EnumSet<MessageCapabilities> getSupportedMessageCapabilities() {
        return messageCapabilities;
    }
}
