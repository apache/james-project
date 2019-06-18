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
package org.apache.james.mailbox.store.mail;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.transaction.Mapper;

/**
 * Maps {@link MailboxMessage} in a {@link org.apache.james.mailbox.MessageManager}. A {@link MessageMapper} has a lifecycle from the start of a request
 * to the end of the request.
 */
public interface MessageMapper extends Mapper {

    /**
     * Return a {@link Iterator} which holds the messages for the given criterias
     * The list must be ordered by the {@link MailboxMessage} uid
     * 
     * @param mailbox The mailbox to search
     * @param set message range for batch processing
     * @param type
     * @param limit the maximal limit of returned {@link MailboxMessage}'s. Use -1 to set no limit. In any case the caller MUST not expect the limit to get applied in all cases as the implementation
     *              MAY just ignore it
     * @throws MailboxException
     */
    Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set, FetchType type, int limit)
            throws MailboxException;

    /**
     * Returns a list of {@link MessageUid} which are marked as deleted
     */
    List<MessageUid> retrieveMessagesMarkedForDeletion(Mailbox mailbox, MessageRange messageRange) throws MailboxException;

    /**
     * Return the count of messages in the mailbox
     * 
     * @param mailbox
     * @return count
     * @throws MailboxException
     */
    long countMessagesInMailbox(Mailbox mailbox)
            throws MailboxException;

    /**
     * Return the count of unseen messages in the mailbox
     * 
     * @param mailbox
     * @return unseenCount
     * @throws MailboxException
     */
    long countUnseenMessagesInMailbox(Mailbox mailbox)
            throws MailboxException;

    MailboxCounters getMailboxCounters(Mailbox mailbox) throws MailboxException;

    /**
     * Delete the given {@link MailboxMessage}
     * 
     * @param mailbox
     * @param message
     * @throws MailboxException
     */
    void delete(Mailbox mailbox, MailboxMessage message) throws MailboxException;

    /**
     * Delete the given list of {@link MessageUid}
     * and return a {@link Map} which holds the uids and metadata for all deleted messages
     */
    Map<MessageUid, MessageMetaData> deleteMessages(Mailbox mailbox, List<MessageUid> uids) throws MailboxException;

    /**
     * Return the uid of the first unseen message. If non can be found null will get returned
     * 
     * 
     * @param mailbox
     * @return uid or null
     * @throws MailboxException
     */
    MessageUid findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException;

    /**
     * Return a List of {@link MailboxMessage} which are recent.
     * The list must be ordered by the {@link MailboxMessage} uid.
     */
    List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException;


    /**
     * Add the given {@link MailboxMessage} to the underlying storage. Be aware that implementation may choose to replace the uid of the given message while storing.
     * So you should only depend on the returned uid.
     * 
     * 
     * @param mailbox
     * @param message
     * @return uid
     * @throws MailboxException
     */
    MessageMetaData add(Mailbox mailbox, MailboxMessage message) throws MailboxException;
    
    /**
     * Update flags for the given {@link MessageRange}. Only the flags may be modified after a message was saved to a mailbox.
     * 
     * @param mailbox
     * @param flagsUpdateCalculator How to update flags
     * @param set
     * @return updatedFlags
     * @throws MailboxException
     */
    Iterator<UpdatedFlags> updateFlags(Mailbox mailbox, FlagsUpdateCalculator flagsUpdateCalculator,
            final MessageRange set) throws MailboxException;
    
    /**
     * Copy the given {@link MailboxMessage} to a new mailbox and return the uid of the copy. Be aware that the given uid is just a suggestion for the uid of the copied
     * message. Implementation may choose to use a different one, so only depend on the returned uid!
     * 
     * @param mailbox the Mailbox to copy to
     * @param original the original to copy
     * @throws MailboxException
     */
    MessageMetaData copy(Mailbox mailbox,MailboxMessage original) throws MailboxException;
    
    /**
     * Move the given {@link MailboxMessage} to a new mailbox and return the uid of the moved. Be aware that the given uid is just a suggestion for the uid of the moved
     * message. Implementation may choose to use a different one, so only depend on the returned uid!
     * 
     * @param mailbox the Mailbox to move to
     * @param original the original to move
     * @throws MailboxException
     */
    MessageMetaData move(Mailbox mailbox,MailboxMessage original) throws MailboxException;
    
    
    /**
     * Return the last uid which were used for storing a MailboxMessage in the {@link Mailbox} or null if no
     */
    Optional<MessageUid> getLastUid(Mailbox mailbox) throws MailboxException;


    /**
     * Return the higest mod-sequence which were used for storing a MailboxMessage in the {@link Mailbox}
     */
    long getHighestModSeq(Mailbox mailbox) throws MailboxException;

    Flags getApplicableFlag(Mailbox mailbox) throws MailboxException;

    /**
     * Return a list containing all MessageUid of Messages that belongs to given {@link Mailbox}
     */
    Iterator<MessageUid> listAllMessageUids(Mailbox mailbox) throws MailboxException;

    /**
     * Specify what data needs to get filled in a {@link MailboxMessage} before returning it
     * 
     *
     */
    enum FetchType {

        /**
         * Fetch only the meta data of the {@link MailboxMessage} which includes:
         * <p>
         *  {@link MailboxMessage#getUid()}
         *  {@link MailboxMessage#getModSeq()}
         *  {@link MailboxMessage#getBodyOctets()}
         *  {@link MailboxMessage#getFullContentOctets()}
         *  {@link MailboxMessage#getInternalDate()}
         *  {@link MailboxMessage#getMailboxId()}
         *  {@link MailboxMessage#getMediaType()}
         *  {@link MailboxMessage#getModSeq()}
         *  {@link MailboxMessage#getSubType()}
         *  {@link MailboxMessage#getTextualLineCount()}
         * </p>
         */
        Metadata,
        /**
         * Fetch the {@link #Metadata}, {@link Property}'s and the {@link #Headers}'s for the {@link MailboxMessage}. This includes:
         * 
         * <p>
         * {@link MailboxMessage#getProperties()}
         * {@link MailboxMessage#getHeaderContent()}
         * </p>
         */
        Headers,
        /**
         * Fetch the {@link #Metadata} and the Body for the {@link MailboxMessage}. This includes:
         * 
         * <p>
         *  {@link MailboxMessage#getBodyContent()}
         * </p>
         */
        Body,
        
        /**
         * Fetch the complete {@link MailboxMessage}
         * 
         */
        Full
    }

}