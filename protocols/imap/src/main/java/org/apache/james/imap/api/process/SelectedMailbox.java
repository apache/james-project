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

import javax.mail.Flags;

import org.apache.james.mailbox.model.MailboxPath;

/**
 * Interface which represent a selected Mailbox during the selected state
 */
public interface SelectedMailbox {

    int NO_SUCH_MESSAGE = -1;

    /**
     * Deselect the Mailbox
     */
    void deselect();

    /**
     * Return the msg index of the given uid or {@link #NO_SUCH_MESSAGE} if no
     * message with the given uid was found
     * 
     * @param uid
     * @return index
     */
    int msn(long uid);

    /**
     * Return the uid of the message for the given index or
     * {@link #NO_SUCH_MESSAGE} if no message with the given index was found
     * 
     * @param index
     * @return uid
     */
    long uid(int index);

    /**
     * Add a recent uid
     * 
     * @param uid
     * @return true if it was successfully
     */
    boolean addRecent(long uid);

    /**
     * Remove a recent uid
     * 
     * @param uid
     * @return true if it was successfully
     */
    boolean removeRecent(long uid);

    /**
     * Return a Collection of all recent uids
     * 
     * @return recentUids
     */
    Collection<Long> getRecent();

    /**
     * Return the count of all recent uids
     * 
     * @return recentCount
     */
    int recentCount();

    /**
     * Return the count of all existing uids
     * 
     * @return existsCount
     */
    long existsCount();

    /**
     * Return the path of the selected Mailbox
     * 
     * @return path
     */
    MailboxPath getPath();

    /**
     * Is the given uid recent ?
     * 
     * @param uid
     * @return true if the given uid is recent
     */
    boolean isRecent(long uid);

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

    /**
     * 
     */
    void resetRecentUidRemoved();


    /**
     * Reset all events
     */
    void resetEvents();

    /**
     * Return a Collection which holds all uids which were expunged
     * 
     * @return expungedUids
     */
    Collection<Long> expungedUids();

    
    void resetExpungedUids();
    
    /**
     * Removes the given UID.
     * 
     * @param uid
     *            not null
     * @return the message sequence number that the UID held before or
     *         {@link #NO_SUCH_MESSAGE} if no message with the given uid was
     *         found being expunged
     */
    int remove(Long uid);

    /**
     * Return a Collection which holds all uids reflecting the Messages which
     * flags were updated
     * 
     * @return flagsUids
     */
    Collection<Long> flagUpdateUids();

    /**
     * Return the uid of the first message in the mailbox or -1 if the mailbox
     * is empty
     * 
     * @return firstUid
     */
    long getFirstUid();

    /**
     * Return the uid of the last message in the mailbox or -1 if the mailbox is
     * empty
     * 
     * @return lastUid
     */
    long getLastUid();
    
    /**
     * Return all applicable Flags for the selected mailbox
     * 
     * @return flags
     */
    Flags getApplicableFlags();
    
    
    boolean hasNewApplicableFlags();
    
    void resetNewApplicableFlags();

}