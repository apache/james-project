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

import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.Flags;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedCriteriaException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResult.FetchGroup;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.SearchQuery;

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
     * Return the count
     * 
     * @param mailboxSession
     * @return count
     * @throws MailboxException
     * @deprecated use
     *             {@link #getMetaData(boolean, MailboxSession, org.apache.james.mailbox.MessageManager.MetaData.FetchGroup)}
     */
    @Deprecated
    long getMessageCount(MailboxSession mailboxSession) throws MailboxException;

    /**
     * Return if the Mailbox is writable
     * 
     * @param session
     * @return writable
     * @throws MailboxException
     * @deprecated use
     *             {@link #getMetaData(boolean, MailboxSession, org.apache.james.mailbox.MessageManager.MetaData.FetchGroup)}
     */
    @Deprecated
    boolean isWriteable(MailboxSession session) throws MailboxException;

    /**
     * Return true if {@link MessageResult#getModSeq()} is stored in a permanent
     * way.
     * 
     * @param session
     * @return modSeqPermanent
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
    Iterator<Long> search(SearchQuery searchQuery, MailboxSession mailboxSession) throws MailboxException;

    /**
     * Expunges messages in the given range from this mailbox.
     * 
     * @param set
     *            not null
     * @param mailboxSession
     *            not null
     * @return uid iterator
     * @throws MailboxException
     *             if anything went wrong
     */
    Iterator<Long> expunge(MessageRange set, MailboxSession mailboxSession) throws MailboxException;

    /**
     * Sets flags on messages within the given range. The new flags are returned
     * for each message altered.
     * 
     * @param flags Flags to be taken into account for transformation of stored flags
     * @param flagsUpdateMode Mode of the transformation of stored flags
     * @param set the range of messages
     * @param mailboxSession not null
     * @return new flags indexed by UID
     * @throws MailboxException
     */
    Map<Long, Flags> setFlags(Flags flags, FlagsUpdateMode flagsUpdateMode, MessageRange set, MailboxSession mailboxSession) throws MailboxException;

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
    long appendMessage(InputStream msgIn, Date internalDate, MailboxSession mailboxSession, boolean isRecent, Flags flags) throws MailboxException;

    /**
     * Gets messages in the given range. The messages may get fetched under
     * the-hood in batches so the caller should check if
     * {@link MessageResultIterator#getException()} returns <code>null</code>
     * after {@link MessageResultIterator#hasNext()} returns <code>false</code>.
     * 
     * 
     * @param set
     * @param fetchGroup
     *            data to fetch
     * @param mailboxSession
     *            not null
     * @return MessageResult with the fields defined by FetchGroup
     * @throws MailboxException
     */
    MessageResultIterator getMessages(MessageRange set, FetchGroup fetchGroup, MailboxSession mailboxSession) throws MailboxException;


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
     * @return meta data, not null
     * @throws MailboxException
     */
    MetaData getMetaData(boolean resetRecent, MailboxSession mailboxSession, MessageManager.MetaData.FetchGroup fetchGroup) throws MailboxException;

    /**
     * Meta data about the current state of the mailbox.
     */
    public interface MetaData {

        /**
         * Describes the optional data types which will get set in the
         * {@link MetaData}.
         * 
         * These are always set: - HIGHESTMODSEQ - PERMANENTFLAGS - UIDNEXT -
         * UIDVALIDITY - MODSEQPERMANET - WRITABLE
         */
        public enum FetchGroup {

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
        };

        /**
         * Gets the UIDs of recent messages if requested or an empty
         * {@link List} otherwise.
         * 
         * @return the uids flagged RECENT in this mailbox,
         */
        List<Long> getRecent();

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
        long getUidNext();

        /**
         * Return the highest mod-sequence for the mailbox. If this value has
         * changed till the last check you can be sure that some changes where
         * happen on the mailbox
         * 
         * @return higestModSeq
         */
        long getHighestModSeq();

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
        Long getFirstUnseen();

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
}
