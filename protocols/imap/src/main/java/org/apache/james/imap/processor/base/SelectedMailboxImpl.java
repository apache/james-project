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

package org.apache.james.imap.processor.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * Default implementation of {@link SelectedMailbox}
 */
public class SelectedMailboxImpl implements SelectedMailbox, MailboxListener{

    private final Set<MessageUid> recentUids = new TreeSet<>();

    private boolean recentUidRemoved = false;

    private final MailboxManager mailboxManager;

    private MailboxPath path;

    private final ImapSession session;

    private final long sessionId;
    private final Set<MessageUid> flagUpdateUids = new TreeSet<>();
    private final Flags.Flag uninterestingFlag = Flags.Flag.RECENT;
    private final Set<MessageUid> expungedUids = new TreeSet<>();
    private final UidMsnConverter uidMsnConverter;

    private boolean isDeletedByOtherSession = false;
    private boolean sizeChanged = false;
    private boolean silentFlagChanges = false;
    private final Flags applicableFlags;

    private boolean applicableFlagsChanged;

    public SelectedMailboxImpl(MailboxManager mailboxManager, ImapSession session, MailboxPath path) throws MailboxException {
        this.session = session;
        this.sessionId = ImapSessionUtils.getMailboxSession(session).getSessionId();
        this.mailboxManager = mailboxManager;
        
        // Ignore events from our session
        setSilentFlagChanges(true);
        this.path = path;

        MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);

        uidMsnConverter = new UidMsnConverter();

        mailboxManager.addListener(path, this, mailboxSession);

        MessageManager messageManager = mailboxManager.getMailbox(path, mailboxSession);
        applicableFlags = messageManager.getApplicableFlags(mailboxSession);
        uidMsnConverter.addAll(ImmutableList.copyOf(
            messageManager.search(new SearchQuery(SearchQuery.all()), mailboxSession)));
    }

    @Override
    public ListenerType getType() {
        return ListenerType.MAILBOX;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return ExecutionMode.SYNCHRONOUS;
    }

    @Override
    public synchronized Optional<MessageUid> getFirstUid() {
        return uidMsnConverter.getFirstUid();
    }

    @Override
    public synchronized Optional<MessageUid> getLastUid() {
        return uidMsnConverter.getLastUid();
    }

    public synchronized void deselect() {
        MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);

        try {
            mailboxManager.removeListener(path, this, mailboxSession);
        } catch (MailboxException e) {
            session.getLog().error("Unable to remove listener " + this + " from mailbox while closing it", e);
        }
        
        uidMsnConverter.clear();
        flagUpdateUids.clear();

        expungedUids.clear();
        recentUids.clear();
    }

    @Override
    public synchronized  boolean removeRecent(MessageUid uid) {
        final boolean result = recentUids.remove(uid);
        if (result) {
            recentUidRemoved = true;
        }
        return result;
    }

    @Override
    public synchronized boolean addRecent(MessageUid uid) {
        return recentUids.add(uid);
    }

    @Override
    public synchronized Collection<MessageUid> getRecent() {
        checkExpungedRecents();
        return new ArrayList<>(recentUids);
    }

    @Override
    public synchronized int recentCount() {
        checkExpungedRecents();
        return recentUids.size();
    }

    @Override
    public synchronized MailboxPath getPath() {
        return path;
    }

    private void checkExpungedRecents() {
        for (MessageUid uid : expungedUids()) {
            removeRecent(uid);
        }
    }

    @Override
    public synchronized boolean isRecent(MessageUid uid) {
        return recentUids.contains(uid);
    }

    @Override
    public synchronized boolean isRecentUidRemoved() {
        return recentUidRemoved;
    }

    @Override
    public synchronized void resetRecentUidRemoved() {
        recentUidRemoved = false;
    }

    @Override
    public synchronized void resetEvents() {
        sizeChanged = false;
        flagUpdateUids.clear();
        isDeletedByOtherSession = false;
        applicableFlagsChanged = false;
    }

    @Override
    public synchronized  int remove(MessageUid uid) {
        final int result = msn(uid);
        uidMsnConverter.remove(uid);
        return result;
    }

    private boolean interestingFlags(UpdatedFlags updated) {
        boolean result;
        final Iterator<Flags.Flag> it = updated.systemFlagIterator();
        if (it.hasNext()) {
            final Flags.Flag flag = it.next();
            if (flag.equals(uninterestingFlag)) {
                result = false;
            } else {
                result = true;
            }
        } else {
            result = false;
        }
        // See if we need to check the user flags
        if (result == false) {
            final Iterator<String> userIt = updated.userFlagIterator();
            result = userIt.hasNext();
        }
        return result;
    }

    
    
    public synchronized void resetExpungedUids() {
        expungedUids.clear();
    }

    /**
     * Are flag changes from current session ignored?
     * 
     * @return true if any flag changes from current session will be ignored,
     *         false otherwise
     */
    public synchronized final boolean isSilentFlagChanges() {
        return silentFlagChanges;
    }

    /**
     * Sets whether changes from current session should be ignored.
     * 
     * @param silentFlagChanges
     *            true if any flag changes from current session should be
     *            ignored, false otherwise
     */
    public synchronized final void setSilentFlagChanges(boolean silentFlagChanges) {
        this.silentFlagChanges = silentFlagChanges;
    }

    /**
     * Has the size of the mailbox changed?
     * 
     * @return true if new messages have been added, false otherwise
     */
    
    public synchronized final boolean isSizeChanged() {
        return sizeChanged;
    }

    /**
     * Is the mailbox deleted?
     * 
     * @return true when the mailbox has been deleted by another session, false
     *         otherwise
     */
    
    public synchronized final boolean isDeletedByOtherSession() {
        return isDeletedByOtherSession;
    }

    /**
     * Return a unmodifiable {@link Collection} of uids which have updated flags
     */
    @Override
    public synchronized Collection<MessageUid> flagUpdateUids() {
        // copy the TreeSet to fix possible
        // java.util.ConcurrentModificationException
        // See IMAP-278
        return Collections.unmodifiableSet(new TreeSet<>(flagUpdateUids));
        
    }

    @Override
    public synchronized Collection<MessageUid> expungedUids() {
        // copy the TreeSet to fix possible
        // java.util.ConcurrentModificationException
        // See IMAP-278
        return Collections.unmodifiableSet(new TreeSet<>(expungedUids));
        
    }

    public synchronized Flags getApplicableFlags() {
        return new Flags(applicableFlags);
    }

    
    public synchronized boolean hasNewApplicableFlags() {
        return applicableFlagsChanged;
    }

    
    public synchronized void resetNewApplicableFlags() {
        applicableFlagsChanged = false;
    }

    
    public synchronized void event(Event event) {

        // Check if the event was for the mailbox we are observing
        if (event.getMailboxPath().equals(getPath())) {
            final long eventSessionId = event.getSession().getSessionId();
            if (event instanceof MessageEvent) {
                final MessageEvent messageEvent = (MessageEvent) event;
                if (messageEvent instanceof Added) {
                    sizeChanged = true;
                    final List<MessageUid> uids = ((Added) event).getUids();
                    SelectedMailbox sm = session.getSelected();
                    for (MessageUid uid : uids) {
                        uidMsnConverter.addUid(uid);
                        if (sm != null) {
                            sm.addRecent(uid);
                        }
                    }
                } else if (messageEvent instanceof FlagsUpdated) {
                    FlagsUpdated updated = (FlagsUpdated) messageEvent;
                    List<UpdatedFlags> uFlags = updated.getUpdatedFlags();
                    if (sessionId != eventSessionId || !silentFlagChanges) {

                        for (UpdatedFlags u : uFlags) {
                            if (interestingFlags(u)) {
                                flagUpdateUids.add(u.getUid());
                            }
                        }
                    }

                    SelectedMailbox sm = session.getSelected();
                    if (sm != null) {
                        // We need to add the UID of the message to the recent
                        // list if we receive an flag update which contains a
                        // \RECENT flag
                        // See IMAP-287
                        List<UpdatedFlags> uflags = updated.getUpdatedFlags();
                        for (UpdatedFlags u : uflags) {
                            Iterator<Flag> flags = u.systemFlagIterator();

                            while (flags.hasNext()) {
                                if (Flag.RECENT.equals(flags.next())) {
                                    MailboxPath path = sm.getPath();
                                    if (path != null && path.equals(event.getMailboxPath())) {
                                        sm.addRecent(u.getUid());
                                    }
                                }
                            }


                        }
                    }
                    
                    int size = applicableFlags.getUserFlags().length;
                    FlagsUpdated updatedF = (FlagsUpdated) messageEvent;
                    List<UpdatedFlags> flags = updatedF.getUpdatedFlags();

                    for (UpdatedFlags flag : flags) {
                        applicableFlags.add(flag.getNewFlags());

                    }

                    // \RECENT is not a applicable flag in imap so remove it
                    // from the list
                    applicableFlags.remove(Flags.Flag.RECENT);

                    if (size < applicableFlags.getUserFlags().length) {
                        applicableFlagsChanged = true;
                    }
                    
                    
                } else if (messageEvent instanceof Expunged) {
                    expungedUids.addAll(messageEvent.getUids());
                    
                }
            } else if (event instanceof MailboxDeletion) {
                if (eventSessionId != sessionId) {
                    isDeletedByOtherSession = true;
                }
            } else if (event instanceof MailboxRenamed) {
                final MailboxRenamed mailboxRenamed = (MailboxRenamed) event;
                path = mailboxRenamed.getNewPath();
            }
        }
    }

    @Override
    public synchronized int msn(MessageUid uid) {
        return uidMsnConverter.getMsn(uid).or(NO_SUCH_MESSAGE);
    }

    @Override
    public synchronized Optional<MessageUid> uid(int msn) {
        if (msn == NO_SUCH_MESSAGE) {
            return Optional.absent();
        }

        return uidMsnConverter.getUid(msn);
    }

    
    public synchronized long existsCount() {
        return uidMsnConverter.getNumMessage();
    }
}
