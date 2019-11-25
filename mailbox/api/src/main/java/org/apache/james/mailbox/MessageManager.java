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

package org.apache.james.mailbox;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxManager.MessageCapabilities;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedCriteriaException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageWriter;

/**
 * Interface which represent a Mailbox
 * 
 * A {@link MessageManager} should be valid for the whole {@link MailboxSession}
 */
public interface MessageManager {

    enum FlagsUpdateMode {
        ADD,
        REMOVE,
        REPLACE
    }

    /**
     * Return the count of messages in the mailbox
     */
    long getMessageCount(MailboxSession mailboxSession) throws MailboxException;

    /**
     * Return the count of unseen messages in the mailbox
     */
    MailboxCounters getMailboxCounters(MailboxSession mailboxSession) throws MailboxException;

    /**
     * Return if the Mailbox is writable
     * @deprecated use
     *             {@link #getMetaData(boolean, MailboxSession, org.apache.james.mailbox.MessageManager.MetaData.FetchGroup)}
     */
    @Deprecated
    boolean isWriteable(MailboxSession session) throws MailboxException;

    /**
     * Return true if {@link MessageResult#getModSeq()} is stored in a permanent
     * way.
     *
     * @deprecated use
     *             {@link #getMetaData(boolean, MailboxSession, org.apache.james.mailbox.MessageManager.MetaData.FetchGroup)}
     */
    boolean isModSeqPermanent(MailboxSession session);

    /**
     * Searches for messages matching the given query. The result must be
     * ordered
     * 
     * @param mailboxSession
     *            not null
     * @return uid iterator
     * @throws UnsupportedCriteriaException
     *             when any of the search parameters are not supported by this
     *             mailbox
     * @throws MailboxException
     *             when search fails for other reasons
     */
    Stream<MessageUid> search(SearchQuery searchQuery, MailboxSession mailboxSession) throws MailboxException;

    /**
     * Expunges messages in the given range from this mailbox by first retrieving the messages to be deleted
     * and then deleting them.
     * 
     * @param set
     *            not null
     * @param mailboxSession
     *            not null
     * @return uid iterator
     * @throws MailboxException
     *             if anything went wrong
     */
    Iterator<MessageUid> expunge(MessageRange set, MailboxSession mailboxSession) throws MailboxException;

    /**
     * Deletes a list of messages given their uids in the mailbox.
     */
    void delete(List<MessageUid> uids, MailboxSession mailboxSession) throws MailboxException;

    /**
     * Sets flags on messages within the given range. The new flags are returned
     * for each message altered.
     * 
     * @param flags Flags to be taken into account for transformation of stored flags
     * @param flagsUpdateMode Mode of the transformation of stored flags
     * @param set the range of messages
     * @param mailboxSession not null
     * @return new flags indexed by UID
     */
    Map<MessageUid, Flags> setFlags(Flags flags, FlagsUpdateMode flagsUpdateMode, MessageRange set, MailboxSession mailboxSession) throws MailboxException;

    /**
     * Appends a message to this mailbox. This method must return a higher UID
     * as the last call in every case which also needs to be unique for the
     * lifetime of the mailbox.
     * 
     * 
     * @param internalDate
     *            the time of addition to be set, not null
     * @param mailboxSession
     *            not null
     * @param isRecent
     *            true when the message should be marked recent, false otherwise
     * @param flags
     *            optionally set these flags on created message, or null when no
     *            additional flags should be set
     * @return uid for the newly added message
     * @throws MailboxException
     *             when message cannot be appended
     */
    ComposedMessageId appendMessage(InputStream msgIn, Date internalDate, MailboxSession mailboxSession, boolean isRecent, Flags flags) throws MailboxException;

    class AppendCommand {

        public static AppendCommand from(Message.Builder builder) throws IOException {
            return builder().build(builder);
        }

        public static AppendCommand from(Message message) throws IOException {
            return builder().build(message);
        }

        public static AppendCommand from(InputStream message) {
            return builder().build(message);
        }

        public static class Builder {
            private Optional<Date> internalDate;
            private Optional<Boolean> isRecent;
            private Optional<Flags> flags;

            private Builder() {
                this.internalDate = Optional.empty();
                this.isRecent = Optional.empty();
                this.flags = Optional.empty();
            }

            public Builder withFlags(Flags flags) {
                this.flags = Optional.of(flags);
                return this;
            }

            public Builder withInternalDate(Date date) {
                this.internalDate = Optional.of(date);
                return this;
            }

            public Builder isRecent(boolean recent) {
                this.isRecent = Optional.of(recent);
                return this;
            }

            public Builder recent() {
                return isRecent(true);
            }

            public Builder notRecent() {
                return isRecent(false);
            }

            public AppendCommand build(InputStream msgIn) {
                return new AppendCommand(
                    msgIn,
                    internalDate.orElse(new Date()),
                    isRecent.orElse(true),
                    flags.orElse(new Flags()));
            }

            public AppendCommand build(byte[] msgIn) {
                return build(new ByteArrayInputStream(msgIn));
            }

            public AppendCommand build(String msgIn) {
                return build(msgIn.getBytes(StandardCharsets.UTF_8));
            }

            public AppendCommand build(Message message) throws IOException {
                return build(DefaultMessageWriter.asBytes(message));
            }

            public AppendCommand build(Message.Builder messageBuilder) throws IOException {
                return build(messageBuilder.build());
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        private final InputStream msgIn;
        private final Date internalDate;
        private final boolean isRecent;
        private final Flags flags;

        private AppendCommand(InputStream msgIn, Date internalDate, boolean isRecent, Flags flags) {
            this.msgIn = msgIn;
            this.internalDate = internalDate;
            this.isRecent = isRecent;
            this.flags = flags;
        }

        public InputStream getMsgIn() {
            return msgIn;
        }

        public Date getInternalDate() {
            return internalDate;
        }

        public boolean isRecent() {
            return isRecent;
        }

        public Flags getFlags() {
            return flags;
        }
    }

    ComposedMessageId appendMessage(AppendCommand appendCommand, MailboxSession session) throws MailboxException;

    /**
     * Gets messages in the given range. The messages may get fetched under
     * the-hood in batches so the caller should check if
     * {@link MessageResultIterator#getException()} returns <code>null</code>
     * after {@link MessageResultIterator#hasNext()} returns <code>false</code>.
     *
     * @param fetchGroup
     *            data to fetch
     * @param mailboxSession
     *            not null
     * @return MessageResult with the fields defined by FetchGroup
     */
    MessageResultIterator getMessages(MessageRange set, FetchGroup fetchGroup, MailboxSession mailboxSession) throws MailboxException;

    /**
     * Return the underlying {@link Mailbox}
     */
    Mailbox getMailboxEntity() throws MailboxException;

    EnumSet<MessageCapabilities> getSupportedMessageCapabilities();

    /**
     * Gets the id of the referenced mailbox
     */
    MailboxId getId();
    
    /**
     * Gets the path of the referenced mailbox
     */
    MailboxPath getMailboxPath() throws MailboxException;

    Flags getApplicableFlags(MailboxSession session) throws MailboxException;

    /**
     * Gets current meta data for the mailbox.<br>
     * Consolidates common calls together to allow improved performance.<br>
     * The meta-data returned should be immutable and represent the current
     * state of the mailbox.
     * 
     * @param resetRecent
     *            true when recent flags should be reset, false otherwise
     * @param mailboxSession
     *            context, not null
     * @param fetchGroup
     *            describes which optional data should be returned
     * @return metadata view filtered for the session's user, not null
     */
    MetaData getMetaData(boolean resetRecent, MailboxSession mailboxSession, MessageManager.MetaData.FetchGroup fetchGroup) throws MailboxException;

    /**
     * Meta data about the current state of the mailbox.
     */
    interface MetaData {

        /**
         * Describes the optional data types which will get set in the
         * {@link MetaData}.
         * 
         * These are always set: - HIGHESTMODSEQ - PERMANENTFLAGS - UIDNEXT -
         * UIDVALIDITY - MODSEQPERMANET - WRITABLE
         */
        enum FetchGroup {

            /**
             * Only include the message and recent count
             */
            NO_UNSEEN,

            /**
             * Only include the unseen message and recent count
             */
            UNSEEN_COUNT,

            /**
             * Only include the first unseen and the recent count
             */
            FIRST_UNSEEN,

            /**
             * Only return the "always set" metadata as documented above
             */
            NO_COUNT
        }

        /**
         * Gets the UIDs of recent messages if requested or an empty
         * {@link List} otherwise.
         * 
         * @return the uids flagged RECENT in this mailbox,
         */
        List<MessageUid> getRecent();

        /**
         * Gets the number of recent messages.
         * 
         * @return the number of messages flagged RECENT in this mailbox
         */
        long countRecent();

        /**
         * Gets the flags which can be stored by this mailbox.
         * 
         * @return Flags that can be stored
         */
        Flags getPermanentFlags();

        /**
         * Gets the UIDVALIDITY.
         * 
         * @return UIDVALIDITY
         */
        long getUidValidity();

        /**
         * Gets the next UID predicted. The returned UID is not guaranteed to be
         * the one that is assigned to the next message. Its only guaranteed
         * that it will be at least equals or bigger then the value
         * 
         * @return the uid that will be assigned to the next appended message
         */
        MessageUid getUidNext();

        /**
         * Return the highest mod-sequence for the mailbox. If this value has
         * changed till the last check you can be sure that some changes where
         * happen on the mailbox
         * 
         * @return higestModSeq
         */
        ModSeq getHighestModSeq();

        /**
         * Gets the number of messages that this mailbox contains. This is an
         * optional property.<br>
         * 
         * @return number of messages contained or -1 when this optional data
         *         has not be requested
         * 
         */
        long getMessageCount();

        /**
         * Gets the number of unseen messages contained in this mailbox. This is
         * an optional property.<br>
         * 
         * @return number of unseen messages contained or zero when this
         *         optional data has not been requested
         * @see FetchGroup#UNSEEN_COUNT
         */
        long getUnseenCount();

        /**
         * Gets the UID of the first unseen message. This is an optional
         * property.<br>
         * 
         * @return uid of the first unseen message, or null when there are no
         *         unseen messages
         * @see FetchGroup#FIRST_UNSEEN
         */
        MessageUid getFirstUnseen();

        /**
         * Is this mailbox writable?
         * 
         * @return true if read-write, false if read only
         */
        boolean isWriteable();

        /**
         * Return true if the mailbox does store the mod-sequences in a
         * permanent way
         * 
         * @return permanent
         */
        boolean isModSeqPermanent();

        /**
         * Returns the ACL concerning this mailbox.
         * 
         * @return acl
         */
        MailboxACL getACL();

    }

    /**
     * Get resolved ACL on this Mailbox for the given Session
     *
     * The result will be the same as calling {MessageManager#getMetaDtata().getAcl()} but will load fewer data
     */
    MailboxACL getResolvedAcl(MailboxSession mailboxSession) throws UnsupportedRightException;
}
