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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.internet.SharedInputStream;
import javax.mail.util.SharedFileInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSession.User;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.ReadOnlyException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLRights;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult.FetchGroup;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMessage;
import org.apache.james.mailbox.store.quota.QuotaChecker;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.streaming.BodyOffsetInputStream;
import org.apache.james.mailbox.store.streaming.CountingInputStream;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.message.DefaultBodyDescriptorBuilder;
import org.apache.james.mime4j.message.HeaderImpl;
import org.apache.james.mime4j.message.MaximalBodyDescriptor;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.apache.james.mime4j.stream.RecursionMode;

/**
 * Base class for {@link org.apache.james.mailbox.MessageManager}
 * implementations.
 * 
 * This base class take care of dispatching events to the registered
 * {@link MailboxListener} and so help with handling concurrent
 * {@link MailboxSession}'s.
 * 
 * 
 * 
 */
public class StoreMessageManager<Id extends MailboxId> implements org.apache.james.mailbox.MessageManager {

    /**
     * The minimal Permanent flags the {@link MessageManager} must support. <br>
     * 
     * <strong>Be sure this static instance will never get modifed
     * later!</strong>
     */
    protected final static Flags MINIMAL_PERMANET_FLAGS;
    static {
        MINIMAL_PERMANET_FLAGS = new Flags();
        MINIMAL_PERMANET_FLAGS.add(Flags.Flag.ANSWERED);
        MINIMAL_PERMANET_FLAGS.add(Flags.Flag.DELETED);
        MINIMAL_PERMANET_FLAGS.add(Flags.Flag.DRAFT);
        MINIMAL_PERMANET_FLAGS.add(Flags.Flag.FLAGGED);
        MINIMAL_PERMANET_FLAGS.add(Flags.Flag.SEEN);
    }

    private final Mailbox<Id> mailbox;

    private final MailboxEventDispatcher<Id> dispatcher;

    private final MessageMapperFactory<Id> mapperFactory;

    private final MessageSearchIndex<Id> index;

    private final MailboxACLResolver aclResolver;

    private final GroupMembershipResolver groupMembershipResolver;

    private final QuotaManager quotaManager;

    private final QuotaRootResolver quotaRootResolver;

    private MailboxPathLocker locker;

    private int fetchBatchSize;

    public StoreMessageManager(final MessageMapperFactory<Id> mapperFactory, final MessageSearchIndex<Id> index, final MailboxEventDispatcher<Id> dispatcher, final MailboxPathLocker locker, final Mailbox<Id> mailbox, final MailboxACLResolver aclResolver,
            final GroupMembershipResolver groupMembershipResolver, final QuotaManager quotaManager, final QuotaRootResolver quotaRootResolver) throws MailboxException {
        this.mailbox = mailbox;
        this.dispatcher = dispatcher;
        this.mapperFactory = mapperFactory;
        this.index = index;
        this.locker = locker;
        this.aclResolver = aclResolver;
        this.groupMembershipResolver = groupMembershipResolver;
        this.quotaManager = quotaManager;
        this.quotaRootResolver = quotaRootResolver;
    }

    public void setFetchBatchSize(int fetchBatchSize) {
        this.fetchBatchSize = fetchBatchSize;
    }

    /**
     * Return the {@link MailboxPathLocker}
     * 
     * @return locker
     */
    protected MailboxPathLocker getLocker() {
        return locker;
    }

    /**
     * Return the {@link MailboxEventDispatcher} for this Mailbox
     * 
     * @return dispatcher
     */
    protected MailboxEventDispatcher<Id> getDispatcher() {
        return dispatcher;
    }

    /**
     * Return the underlying {@link Mailbox}
     * 
     * @return mailbox
     * @throws MailboxException
     */

    public Mailbox<Id> getMailboxEntity() throws MailboxException {
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
     * 
     * @param session
     * @return flags
     */
    protected Flags getPermanentFlags(MailboxSession session) {

        // Return a new flags instance to make sure the static declared flags
        // instance will not get modified later.
        //
        // See MAILBOX-109
        return new Flags(MINIMAL_PERMANET_FLAGS);
    }

    /**
     * Returns the flags which are shared for the current mailbox, i.e. the
     * flags set up so that changes to those flags are visible to another user.
     * See RFC 4314 section 5.2.
     * 
     * In this implementation, all permanent flags are shared, ergo we simply
     * return {@link #getPermanentFlags(MailboxSession)}
     * 
     * @see UnionMailboxACLResolver#isReadWrite(MailboxACLRights, Flags)
     * 
     * @param session
     * @return
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
    public boolean isModSeqPermanent(MailboxSession session) {
        return true;
    }

    /**
     * @see org.apache.james.mailbox.MessageManager#expunge(org.apache.james.mailbox.model.MessageRange,
     *      org.apache.james.mailbox.MailboxSession)
     */
    public Iterator<Long> expunge(final MessageRange set, MailboxSession mailboxSession) throws MailboxException {
        if (!isWriteable(mailboxSession)) {
            throw new ReadOnlyException(new StoreMailboxPath<Id>(getMailboxEntity()), mailboxSession.getPathDelimiter());
        }
        Map<Long, MessageMetaData> uids = deleteMarkedInMailbox(set, mailboxSession);

        dispatcher.expunged(mailboxSession, uids, getMailboxEntity());
        return uids.keySet().iterator();
    }

    /**
     * @see org.apache.james.mailbox.MessageManager#appendMessage(java.io.InputStream,
     *      java.util.Date, org.apache.james.mailbox.MailboxSession, boolean,
     *      javax.mail.Flags)
     */
    public long appendMessage(final InputStream msgIn, Date internalDate, final MailboxSession mailboxSession, final boolean isRecent, final Flags flagsToBeSet) throws MailboxException {

        File file = null;
        TeeInputStream tmpMsgIn = null;
        BodyOffsetInputStream bIn = null;
        FileOutputStream out = null;
        SharedFileInputStream contentIn = null;

        if (!isWriteable(mailboxSession)) {
            throw new ReadOnlyException(new StoreMailboxPath<Id>(getMailboxEntity()), mailboxSession.getPathDelimiter());
        }

        try {
            // Create a temporary file and copy the message to it. We will work
            // with the file as
            // source for the InputStream
            file = File.createTempFile("imap", ".msg");
            out = new FileOutputStream(file);

            tmpMsgIn = new TeeInputStream(msgIn, out);

            bIn = new BodyOffsetInputStream(tmpMsgIn);
            // Disable line length... This should be handled by the smtp server
            // component and not the parser itself
            // https://issues.apache.org/jira/browse/IMAP-122
            MimeConfig config = MimeConfig.custom().setMaxLineLen(-1).setMaxHeaderLen(-1).build();

            final MimeTokenStream parser = new MimeTokenStream(config, new DefaultBodyDescriptorBuilder());

            parser.setRecursionMode(RecursionMode.M_NO_RECURSE);
            parser.parse(bIn);
            final HeaderImpl header = new HeaderImpl();

            EntityState next = parser.next();
            while (next != EntityState.T_BODY && next != EntityState.T_END_OF_STREAM && next != EntityState.T_START_MULTIPART) {
                if (next == EntityState.T_FIELD) {
                    header.addField(parser.getField());
                }
                next = parser.next();
            }
            final MaximalBodyDescriptor descriptor = (MaximalBodyDescriptor) parser.getBodyDescriptor();
            final PropertyBuilder propertyBuilder = new PropertyBuilder();
            final String mediaType;
            final String mediaTypeFromHeader = descriptor.getMediaType();
            final String subType;
            if (mediaTypeFromHeader == null) {
                mediaType = "text";
                subType = "plain";
            } else {
                mediaType = mediaTypeFromHeader;
                subType = descriptor.getSubType();
            }
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

            final Flags flags;
            if (flagsToBeSet == null) {
                flags = new Flags();
            } else {
                flags = flagsToBeSet;

                // Check if we need to trim the flags
                trimFlags(flags, mailboxSession);

            }
            if (isRecent) {
                flags.add(Flags.Flag.RECENT);
            }
            if (internalDate == null) {
                internalDate = new Date();
            }
            byte[] discard = new byte[4096];
            while (tmpMsgIn.read(discard) != -1) {
                // consume the rest of the stream so everything get copied to
                // the file now
                // via the TeeInputStream
            }
            int bodyStartOctet = (int) bIn.getBodyStartOffset();
            if (bodyStartOctet == -1) {
                bodyStartOctet = 0;
            }
            contentIn = new SharedFileInputStream(file);
            final int size = (int) file.length();

            final Message<Id> message = createMessage(internalDate, size, bodyStartOctet, contentIn, flags, propertyBuilder);

            new QuotaChecker(quotaManager, quotaRootResolver, mailbox).tryAddition(1, size);

            return locker.executeWithLock(mailboxSession, new StoreMailboxPath<Id>(getMailboxEntity()), new MailboxPathLocker.LockAwareExecution<Long>() {

                @Override
                public Long execute() throws MailboxException {
                    MessageMetaData data = appendMessageToStore(message, mailboxSession);

                    SortedMap<Long, MessageMetaData> uids = new TreeMap<Long, MessageMetaData>();
                    uids.put(data.getUid(), data);
                    dispatcher.added(mailboxSession, uids, getMailboxEntity());
                    return data.getUid();
                }
            }, true);

        } catch (IOException e) {
            throw new MailboxException("Unable to parse message", e);
        } catch (MimeException e) {
            throw new MailboxException("Unable to parse message", e);
        } finally {
            IOUtils.closeQuietly(bIn);
            IOUtils.closeQuietly(tmpMsgIn);
            IOUtils.closeQuietly(out);
            IOUtils.closeQuietly(contentIn);

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

    /**
     * Create a new {@link Message} for the given data
     * 
     * @param internalDate
     * @param size
     * @param bodyStartOctet
     * @param content
     * @param flags
     * @return membership
     * @throws MailboxException
     */
    protected Message<Id> createMessage(Date internalDate, final int size, int bodyStartOctet, final SharedInputStream content, final Flags flags, final PropertyBuilder propertyBuilder) throws MailboxException {
        return new SimpleMessage<Id>(internalDate, size, bodyStartOctet, content, flags, propertyBuilder, getMailboxEntity().getMailboxId());
    }

    /**
     * This mailbox is writable
     * 
     * @throws MailboxException
     */
    public boolean isWriteable(MailboxSession session) throws MailboxException {
        return aclResolver.isReadWrite(myRights(session), getSharedPermanentFlags(session));
    }

    /**
     * @see MessageManager#getMetaData(boolean, MailboxSession,
     *      org.apache.james.mailbox.MessageManager.MetaData.FetchGroup)
     */
    public MetaData getMetaData(boolean resetRecent, MailboxSession mailboxSession, org.apache.james.mailbox.MessageManager.MetaData.FetchGroup fetchGroup) throws MailboxException {

        final List<Long> recent;
        final Flags permanentFlags = getPermanentFlags(mailboxSession);
        final long uidValidity = getMailboxEntity().getUidValidity();
        final long uidNext = mapperFactory.getMessageMapper(mailboxSession).getLastUid(mailbox) + 1;
        final long highestModSeq = mapperFactory.getMessageMapper(mailboxSession).getHighestModSeq(mailbox);
        final long messageCount;
        final long unseenCount;
        final Long firstUnseen;
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
            recent = new ArrayList<Long>();
            break;
        }
        MailboxACL resolvedAcl = getResolvedMailboxACL(mailboxSession);
        return new MailboxMetaData(recent, permanentFlags, uidValidity, uidNext, highestModSeq, messageCount, unseenCount, firstUnseen, isWriteable(mailboxSession), isModSeqPermanent(mailboxSession), resolvedAcl);
    }

    /**
     * Check if the given {@link Flags} contains {@link Flags} which are not
     * included in the returned {@link Flags} of
     * {@link #getPermanentFlags(MailboxSession)}. If any are found, these are
     * removed from the given {@link Flags} instance. The only exception is the
     * {@link Flag#RECENT} flag.
     * 
     * This flag is never removed!
     * 
     * @param flags
     * @param session
     */
    private void trimFlags(Flags flags, MailboxSession session) {

        Flags permFlags = getPermanentFlags(session);

        Flag[] systemFlags = flags.getSystemFlags();
        for (int i = 0; i < systemFlags.length; i++) {
            Flag f = systemFlags[i];

            if (f != Flag.RECENT && permFlags.contains(f) == false) {
                flags.remove(f);
            }
        }
        // if the permFlags contains the special USER flag we can skip this as
        // all user flags are allowed
        if (permFlags.contains(Flags.Flag.USER) == false) {
            String[] uFlags = flags.getUserFlags();
            for (int i = 0; i < uFlags.length; i++) {
                String uFlag = uFlags[i];
                if (permFlags.contains(uFlag) == false) {
                    flags.remove(uFlag);
                }
            }
        }

    }

    /**
     * @see org.apache.james.mailbox.MessageManager#setFlags(javax.mail.Flags,
     *      boolean, boolean, org.apache.james.mailbox.model.MessageRange,
     *      org.apache.james.mailbox.MailboxSession)
     */
    public Map<Long, Flags> setFlags(final Flags flags, final FlagsUpdateMode flagsUpdateMode, final MessageRange set, MailboxSession mailboxSession) throws MailboxException {

        if (!isWriteable(mailboxSession)) {
            throw new ReadOnlyException(new StoreMailboxPath<Id>(getMailboxEntity()), mailboxSession.getPathDelimiter());
        }
        final SortedMap<Long, Flags> newFlagsByUid = new TreeMap<Long, Flags>();

        trimFlags(flags, mailboxSession);

        final MessageMapper<Id> messageMapper = mapperFactory.getMessageMapper(mailboxSession);

        Iterator<UpdatedFlags> it = messageMapper.execute(new Mapper.Transaction<Iterator<UpdatedFlags>>() {

            public Iterator<UpdatedFlags> run() throws MailboxException {
                return messageMapper.updateFlags(getMailboxEntity(), new FlagsUpdateCalculator(flags, flagsUpdateMode), set);
            }
        });

        final SortedMap<Long, UpdatedFlags> uFlags = new TreeMap<Long, UpdatedFlags>();

        while (it.hasNext()) {
            UpdatedFlags flag = it.next();
            newFlagsByUid.put(flag.getUid(), flag.getNewFlags());
            uFlags.put(flag.getUid(), flag);
        }

        dispatcher.flagsUpdated(mailboxSession, new ArrayList<Long>(uFlags.keySet()), getMailboxEntity(), new ArrayList<UpdatedFlags>(uFlags.values()));

        return newFlagsByUid;
    }

    /**
     * Copy the {@link MessageRange} to the {@link StoreMessageManager}
     * 
     * @param set
     * @param toMailbox
     * @param session
     * @throws MailboxException
     */
    public List<MessageRange> copyTo(final MessageRange set, final StoreMessageManager<Id> toMailbox, final MailboxSession session) throws MailboxException {
        if (!toMailbox.isWriteable(session)) {
            throw new ReadOnlyException(new StoreMailboxPath<Id>(toMailbox.getMailboxEntity()), session.getPathDelimiter());
        }

        return locker.executeWithLock(session, new StoreMailboxPath<Id>(toMailbox.getMailboxEntity()), new MailboxPathLocker.LockAwareExecution<List<MessageRange>>() {

            @Override
            public List<MessageRange> execute() throws MailboxException {
                SortedMap<Long, MessageMetaData> copiedUids = copy(set, toMailbox, session);
                dispatcher.added(session, copiedUids, toMailbox.getMailboxEntity());
                return MessageRange.toRanges(new ArrayList<Long>(copiedUids.keySet()));
            }
        }, true);
    }

    /**
     * Move the {@link MessageRange} to the {@link StoreMessageManager}
     * 
     * @param set
     * @param toMailbox
     * @param session
     * @throws MailboxException
     */
    public List<MessageRange> moveTo(final MessageRange set, final StoreMessageManager<Id> toMailbox, final MailboxSession session) throws MailboxException {
        if (!isWriteable(session)) {
            throw new ReadOnlyException(new StoreMailboxPath<Id>(getMailboxEntity()), session.getPathDelimiter());
        }
        if (!toMailbox.isWriteable(session)) {
            throw new ReadOnlyException(new StoreMailboxPath<Id>(toMailbox.getMailboxEntity()), session.getPathDelimiter());
        }

        //TODO lock the from mailbox too, in a non-deadlocking manner - how?
        return locker.executeWithLock(session, new StoreMailboxPath<Id>(toMailbox.getMailboxEntity()), new MailboxPathLocker.LockAwareExecution<List<MessageRange>>() {

            @Override
            public List<MessageRange> execute() throws MailboxException {
                SortedMap<Long, MessageMetaData> movedUids = move(set, toMailbox, session);
                dispatcher.added(session, movedUids, toMailbox.getMailboxEntity());
                return MessageRange.toRanges(new ArrayList<Long>(movedUids.keySet()));
            }
        }, true);
    }

    protected MessageMetaData appendMessageToStore(final Message<Id> message, MailboxSession session) throws MailboxException {
        final MessageMapper<Id> mapper = mapperFactory.getMessageMapper(session);
        return mapperFactory.getMessageMapper(session).execute(new Mapper.Transaction<MessageMetaData>() {

            public MessageMetaData run() throws MailboxException {
                return mapper.add(getMailboxEntity(), message);
            }

        });
    }

    /**
     * @see org.apache.james.mailbox.MessageManager#getMessageCount(org.apache.james.mailbox.MailboxSession)
     */
    public long getMessageCount(MailboxSession mailboxSession) throws MailboxException {
        return mapperFactory.getMessageMapper(mailboxSession).countMessagesInMailbox(getMailboxEntity());
    }

    /**
     * @see org.apache.james.mailbox.MessageManager#getMessages(org.apache.james.mailbox.model.MessageRange,
     *      org.apache.james.mailbox.model.MessageResult.FetchGroup,
     *      org.apache.james.mailbox.MailboxSession)
     */
    public MessageResultIterator getMessages(final MessageRange set, FetchGroup fetchGroup, MailboxSession mailboxSession) throws MailboxException {
        final MessageMapper<Id> messageMapper = mapperFactory.getMessageMapper(mailboxSession);
        return new StoreMessageResultIterator<Id>(messageMapper, mailbox, set, fetchBatchSize, fetchGroup);
    }

    /**
     * Return a List which holds all uids of recent messages and optional reset
     * the recent flag on the messages for the uids
     * 
     * @param reset
     * @param mailboxSession
     * @return list
     * @throws MailboxException
     */
    protected List<Long> recent(final boolean reset, MailboxSession mailboxSession) throws MailboxException {
        if (reset) {
            if (!isWriteable(mailboxSession)) {
                throw new ReadOnlyException(new StoreMailboxPath<Id>(getMailboxEntity()), mailboxSession.getPathDelimiter());
            }
        }
        final MessageMapper<Id> messageMapper = mapperFactory.getMessageMapper(mailboxSession);

        return messageMapper.execute(new Mapper.Transaction<List<Long>>() {

            public List<Long> run() throws MailboxException {
                final List<Long> members = messageMapper.findRecentMessageUidsInMailbox(getMailboxEntity());

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
            }

        });

    }

    protected Map<Long, MessageMetaData> deleteMarkedInMailbox(final MessageRange range, final MailboxSession session) throws MailboxException {

        final MessageMapper<Id> messageMapper = mapperFactory.getMessageMapper(session);

        return messageMapper.execute(new Mapper.Transaction<Map<Long, MessageMetaData>>() {

            public Map<Long, MessageMetaData> run() throws MailboxException {
                return messageMapper.expungeMarkedForDeletionInMailbox(getMailboxEntity(), range);
            }

        });
    }

    /**
     * @see org.apache.james.mailbox.MessageManager#search(org.apache.james.mailbox.model.SearchQuery,
     *      org.apache.james.mailbox.MailboxSession)
     */
    public Iterator<Long> search(SearchQuery query, MailboxSession mailboxSession) throws MailboxException {
        return index.search(mailboxSession, getMailboxEntity(), query);
    }

    private Iterator<MessageMetaData> copy(final Iterator<Message<Id>> originalRows, final MailboxSession session) throws MailboxException {
        final List<MessageMetaData> copiedRows = new ArrayList<MessageMetaData>();
        final MessageMapper<Id> messageMapper = mapperFactory.getMessageMapper(session);
        QuotaChecker quotaChecker = new QuotaChecker(quotaManager, quotaRootResolver, mailbox);

        while (originalRows.hasNext()) {
            final Message<Id> originalMessage = originalRows.next();
            quotaChecker.tryAddition(1, originalMessage.getFullContentOctets());
            MessageMetaData data = messageMapper.execute(new Mapper.Transaction<MessageMetaData>() {
                public MessageMetaData run() throws MailboxException {
                    return messageMapper.copy(getMailboxEntity(), originalMessage);

                }

            });
            copiedRows.add(data);
        }
        return copiedRows.iterator();
    }

    private Iterator<MessageMetaData> move(Iterator<Message<Id>> originalRows,
			MailboxSession session) throws MailboxException {
        final List<MessageMetaData> movedRows = new ArrayList<MessageMetaData>();
        final MessageMapper<Id> messageMapper = mapperFactory.getMessageMapper(session);

        while (originalRows.hasNext()) {
            final Message<Id> originalMessage = originalRows.next();
            MessageMetaData data = messageMapper.execute(new Mapper.Transaction<MessageMetaData>() {
                public MessageMetaData run() throws MailboxException {
                    return messageMapper.move(getMailboxEntity(), originalMessage);

                }

            });
            movedRows.add(data);
        }
        return movedRows.iterator();
	}


    /**
     * @see org.apache.james.mailbox.store.AbstractStoreMessageManager#copy(org.apache.james.mailbox.model.MessageRange,
     *      org.apache.james.mailbox.store.AbstractStoreMessageManager,
     *      org.apache.james.mailbox.MailboxSession)
     */
    private SortedMap<Long, MessageMetaData> copy(MessageRange set, final StoreMessageManager<Id> to, final MailboxSession session) throws MailboxException {
        MessageMapper<Id> messageMapper = mapperFactory.getMessageMapper(session);

        final SortedMap<Long, MessageMetaData> copiedMessages = new TreeMap<Long, MessageMetaData>();
        Iterator<Message<Id>> originalRows = messageMapper.findInMailbox(mailbox, set, FetchType.Full, -1);
        Iterator<MessageMetaData> ids = to.copy(originalRows, session);
        while (ids.hasNext()) {
            MessageMetaData data = ids.next();
            copiedMessages.put(data.getUid(), data);
        }

        return copiedMessages;
    }

    private SortedMap<Long, MessageMetaData> move(MessageRange set,
			final StoreMessageManager<Id> to, final MailboxSession session) throws MailboxException {
        MessageMapper<Id> messageMapper = mapperFactory.getMessageMapper(session);

        final SortedMap<Long, MessageMetaData> movedMessages = new TreeMap<Long, MessageMetaData>();
        Iterator<Message<Id>> originalRows = messageMapper.findInMailbox(mailbox, set, FetchType.Full, -1);
        Iterator<MessageMetaData> ids = to.move(originalRows, session);
        while (ids.hasNext()) {
            MessageMetaData data = ids.next();
            movedMessages.put(data.getUid(), data);
        }

        return movedMessages;
	}


    /**
     * Return the count of unseen messages
     * 
     * @param session
     * @return count of unseen messages
     * @throws MailboxException
     */
    protected long countUnseenMessagesInMailbox(MailboxSession session) throws MailboxException {
        MessageMapper<Id> messageMapper = mapperFactory.getMessageMapper(session);
        return messageMapper.countUnseenMessagesInMailbox(getMailboxEntity());
    }

    /**
     * Return the uid of the first unseen message or null of none is found
     * 
     * @param session
     * @return uid
     * @throws MailboxException
     */
    protected Long findFirstUnseenMessageUid(MailboxSession session) throws MailboxException {
        MessageMapper<Id> messageMapper = mapperFactory.getMessageMapper(session);
        return messageMapper.findFirstUnseenMessageUid(getMailboxEntity());
    }

    private MailboxACLRights myRights(MailboxSession session) throws MailboxException {
        User user = session.getUser();
        if (user != null) {
            return aclResolver.resolveRights(user.getUserName(), groupMembershipResolver, mailbox.getACL(), mailbox.getUser(), new GroupFolderResolver(session).isGroupFolder(mailbox));
        } else {
            return SimpleMailboxACL.NO_RIGHTS;
        }
    }

    /**
     * Applies the global ACL (if there are any) to the mailbox ACL.
     * 
     * @param mailboxSession
     * @return the ACL of the present mailbox merged with the global ACL (if
     *         there are any).
     * @throws UnsupportedRightException
     */
    protected MailboxACL getResolvedMailboxACL(MailboxSession mailboxSession) throws UnsupportedRightException {
        return aclResolver.applyGlobalACL(mailbox.getACL(), new GroupFolderResolver(mailboxSession).isGroupFolder(mailbox));
    }

}
