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

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.transaction.Mapper;

/**
 * Maps {@link Message} in a {@link org.apache.james.mailbox.MessageManager}. A {@link MessageMapper} has a lifecycle from the start of a request 
 * to the end of the request.
 */
public interface MessageMapper<Id extends MailboxId> extends Mapper {

    /**
     * Return a {@link Iterator} which holds the messages for the given criterias
     * The list must be ordered by the {@link Message} uid
     * 
     * @param mailbox The mailbox to search
     * @param set message range for batch processing
     * @param type
     * @param limit the maximal limit of returned {@link Message}'s. Use -1 to set no limit. In any case the caller MUST not expect the limit to get applied in all cases as the implementation
     *              MAY just ignore it
     * @throws MailboxException
     */
    Iterator<Message<Id>> findInMailbox(Mailbox<Id> mailbox, MessageRange set, FetchType type, int limit)
            throws MailboxException;

    /**
     * Return a {@link Iterator} which holds the uids for all deleted Messages for the given {@link MessageRange} which are marked for deletion
     * The list must be ordered
     * @param mailbox
     * @param set 
     * @return uids
     * @throws MailboxException
     */
    Map<Long, MessageMetaData> expungeMarkedForDeletionInMailbox(
            Mailbox<Id> mailbox, final MessageRange set)
            throws MailboxException;

    /**
     * Return the count of messages in the mailbox
     * 
     * @param mailbox
     * @return count
     * @throws MailboxException
     */
    long countMessagesInMailbox(Mailbox<Id> mailbox)
            throws MailboxException;

    /**
     * Return the count of unseen messages in the mailbox
     * 
     * @param mailbox
     * @return unseenCount
     * @throws StorageException
     */
    long countUnseenMessagesInMailbox(Mailbox<Id> mailbox)
            throws MailboxException;


    /**
     * Delete the given {@link Message}
     * 
     * @param mailbox
     * @param message
     * @throws StorageException
     */
    void delete(Mailbox<Id> mailbox, Message<Id> message) throws MailboxException;

    /**
     * Return the uid of the first unseen message. If non can be found null will get returned
     * 
     * 
     * @param mailbox
     * @return uid or null
     * @throws StorageException
     */
    Long findFirstUnseenMessageUid(Mailbox<Id> mailbox) throws MailboxException;

    /**
     * Return a List of {@link Message} which are recent.
     * The list must be ordered by the {@link Message} uid. 
     * 
     * @param mailbox
     * @return recentList
     * @throws StorageException
     */
    List<Long> findRecentMessageUidsInMailbox(Mailbox<Id> mailbox) throws MailboxException;


    /**
     * Add the given {@link Message} to the underlying storage. Be aware that implementation may choose to replace the uid of the given message while storing.
     * So you should only depend on the returned uid.
     * 
     * 
     * @param mailbox
     * @param message
     * @return uid
     * @throws StorageException
     */
    MessageMetaData add(Mailbox<Id> mailbox, Message<Id> message) throws MailboxException;
    
    /**
     * Update flags for the given {@link MessageRange}. Only the flags may be modified after a message was saved to a mailbox.
     * 
     * @param mailbox
     * @param flagsUpdateCalculator How to update flags
     * @param set
     * @return updatedFlags
     * @throws MailboxException
     */
    Iterator<UpdatedFlags> updateFlags(Mailbox<Id> mailbox, final FlagsUpdateCalculator flagsUpdateCalculator,
            final MessageRange set) throws MailboxException;
    
    /**
     * Copy the given {@link Message} to a new mailbox and return the uid of the copy. Be aware that the given uid is just a suggestion for the uid of the copied
     * message. Implementation may choose to use a different one, so only depend on the returned uid!
     * 
     * @param mailbox the Mailbox to copy to
     * @param original the original to copy
     * @throws StorageException
     */
    MessageMetaData copy(Mailbox<Id> mailbox,Message<Id> original) throws MailboxException;
    
    /**
     * Move the given {@link Message} to a new mailbox and return the uid of the moved. Be aware that the given uid is just a suggestion for the uid of the moved
     * message. Implementation may choose to use a different one, so only depend on the returned uid!
     * 
     * @param mailbox the Mailbox to move to
     * @param original the original to move
     * @throws StorageException
     */
    MessageMetaData move(Mailbox<Id> mailbox,Message<Id> original) throws MailboxException;
    
    
    /**
     * Return the last uid which were used for storing a Message in the {@link Mailbox}
     * 
     * @param mailbox
     * @return lastUid
     * @throws MailboxException
     */
    long getLastUid(Mailbox<Id> mailbox) throws MailboxException;;
    
    
    /**
     * Return the higest mod-sequence which were used for storing a Message in the {@link Mailbox}
     * 
     * @param mailbox
     * @return lastUid
     * @throws MailboxException
     */
    long getHighestModSeq(Mailbox<Id> mailbox) throws MailboxException;
    
    /**
     * Specify what data needs to get filled in a {@link Message} before returning it
     * 
     *
     */
    public static enum FetchType {

        /**
         * Fetch only the meta data of the {@link Message} which includes:
         * <p>
         *  {@link Message#getUid()}
         *  {@link Message#getModSeq()}
         *  {@link Message#getBodyOctets()}
         *  {@link Message#getFullContentOctets()}
         *  {@link Message#getInternalDate()}
         *  {@link Message#getMailboxId()}
         *  {@link Message#getMediaType()}
         *  {@link Message#getModSeq()}
         *  {@link Message#getSubType()}
         *  {@link Message#getTextualLineCount()}
         * </p>
         */
        Metadata,
        /**
         * Fetch the {@link #Metadata}, {@link Property}'s and the {@link #Headers}'s for the {@link Message}. This includes:
         * 
         * <p>
         * {@link Message#getProperties()}
         * {@link Message#getHeaderContent()}
         * </p>
         */
        Headers,
        /**
         * Fetch the {@link #Metadata} and the Body for the {@link Message}. This includes:
         * 
         * <p>
         *  {@link Message#getBodyContent()}
         * </p>
         */
        Body,
        
        /**
         * Fetch the complete {@link Message}
         * 
         */
        Full
    }

}