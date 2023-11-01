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

package org.apache.james.imap.api.process;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.events.EventListener;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.NullableMessageSequenceNumber;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;

import reactor.core.publisher.Mono;

/**
 * Interface which represent a selected Mailbox during the selected state
 */
public interface SelectedMailbox {

    int NO_SUCH_MESSAGE = -1;

    /**
     * Deselect the Mailbox
     */
    Mono<Void> deselect();

    void registerIdle(EventListener.ReactiveEventListener idle);

    void unregisterIdle();

    /**
     * Return the msg index of the given uid or {@link NullableMessageSequenceNumber#noMessage()} instance if no
     * message with the given uid was found
     */
    NullableMessageSequenceNumber msn(MessageUid uid);

    /**
     * Return the uid of the message for the given index or empty if no message with the given index was found
     */
    Optional<MessageUid> uid(int index);

    /**
     * Add a recent uid
     * 
     * @return true if it was successfully
     */
    boolean addRecent(MessageUid uid);

    /**
     * Remove a recent uid
     * 
     * @return true if it was successfully
     */
    boolean removeRecent(MessageUid uid);

    /**
     * Return a Collection of all recent uids
     */
    Collection<MessageUid> getRecent();

    /**
     * Return the count of all recent uids
     */
    int recentCount();

    /**
     * Return the count of all existing uids
     */
    long existsCount();

    Mono<MailboxPath> getPathReactive();

    /**
     * Return the mailboxId of the selected Mailbox.
     *
     * This is beneficial as the MailboxId is immutable.
     */
    MailboxId getMailboxId();

    MessageManager getMessageManager();

    /**
     * Is the given uid recent ?
     * 
     * @return true if the given uid is recent
     */
    boolean isRecent(MessageUid uid);

    /**
     * Is the mailbox deleted?
     * 
     * @return true when the mailbox has been deleted by another session, false
     *         otherwise
     */
    boolean isDeletedByOtherSession();

    /**
     * Is the size of the mailbox changed ?
     * 
     * @return true if the mailbox size was changed
     */
    boolean isSizeChanged();

    /**
     * Was the recent uid removed ?
     * 
     * @return true if the recent uid for this mailbox was removed
     */
    boolean isRecentUidRemoved();

    void resetRecentUidRemoved();


    /**
     * Reset all events
     */
    void resetEvents();

    /**
     * Return a Collection which holds all uids which were expunged
     */
    Collection<MessageUid> expungedUids();

    
    void resetExpungedUids();
    
    /**
     * Removes the given UID.
     * 
     * @param uid
     *            not null
     * @return the message sequence number that the UID held before or
     *         {@link NullableMessageSequenceNumber#noMessage()} instance if no message with the given uid was
     *         found being expunged
     */
    NullableMessageSequenceNumber remove(MessageUid uid);

    /**
     * Return a Collection which holds all uids reflecting the Messages which
     * flags were updated
     */
    Collection<MessageUid> flagUpdateUids();

    /**
     * Return the uid of the first message in the mailbox or empty if the mailbox
     * is empty
     */
    Optional<MessageUid> getFirstUid();

    /**
     * Return the uid of the last message in the mailbox or empty if the mailbox is
     * empty
     */
    Optional<MessageUid> getLastUid();

    List<MessageUid> allUids();
    
    /**
     * Return all applicable Flags for the selected mailbox
     */
    Flags getApplicableFlags();
    
    
    boolean hasNewApplicableFlags();
    
    void resetNewApplicableFlags();

}