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
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.NullableMessageSequenceNumber;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.events.Registration;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;

import com.github.steveash.guavate.Guavate;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Default implementation of {@link SelectedMailbox}
 */
public class SelectedMailboxImpl implements SelectedMailbox, MailboxListener {


    private static class ApplicableFlags {
        static ApplicableFlags from(Flags flags) {
            boolean updated = false;
            return new ApplicableFlags(flags, updated);
        }

        private final Flags flags;
        private final boolean updated;

        private ApplicableFlags(Flags flags, boolean updated) {
            this.flags = flags;
            this.updated = updated;
        }

        public ApplicableFlags ackUpdates() {
            return new ApplicableFlags(flags, false);
        }

        public Flags flags() {
            return new Flags(flags);
        }

        public boolean updated() {
            return updated;
        }

        public ApplicableFlags update(boolean applicableFlagsChanged) {
            return new ApplicableFlags(flags, true);
        }
    }

    private final Registration registration;
    private final MailboxManager mailboxManager;
    private final MailboxId mailboxId;
    private final ImapSession session;
    private final MailboxSession.SessionId sessionId;
    private final MailboxSession mailboxSession;
    private final UidMsnConverter uidMsnConverter;
    private final Set<MessageUid> recentUids = new TreeSet<>();
    private final Set<MessageUid> flagUpdateUids = new TreeSet<>();
    private final Flags.Flag uninterestingFlag = Flags.Flag.RECENT;
    private final Set<MessageUid> expungedUids = new TreeSet<>();

    private boolean recentUidRemoved = false;
    private boolean isDeletedByOtherSession = false;
    private boolean sizeChanged = false;
    private boolean silentFlagChanges = false;
    private ApplicableFlags applicableFlags;

    public SelectedMailboxImpl(MailboxManager mailboxManager, EventBus eventBus, ImapSession session, MessageManager messageManager) throws MailboxException {
        this.session = session;
        this.sessionId = session.getMailboxSession().getSessionId();
        this.mailboxManager = mailboxManager;
        
        // Ignore events from our session
        setSilentFlagChanges(true);

        mailboxSession = session.getMailboxSession();

        uidMsnConverter = new UidMsnConverter();

        mailboxId = messageManager.getId();

        registration = Mono.from(eventBus.register(this, new MailboxIdRegistrationKey(mailboxId)))
            .subscribeOn(Schedulers.elastic())
            .block();

        applicableFlags = ApplicableFlags.from(messageManager.getApplicableFlags(mailboxSession));
        try (Stream<MessageUid> stream = messageManager.search(SearchQuery.of(SearchQuery.all()), mailboxSession)) {
            uidMsnConverter.addAll(stream.collect(Guavate.toImmutableList()));
        }
    }

    @Override
    public synchronized Optional<MessageUid> getFirstUid() {
        return uidMsnConverter.getFirstUid();
    }

    @Override
    public synchronized Optional<MessageUid> getLastUid() {
        return uidMsnConverter.getLastUid();
    }

    @Override
    public synchronized void deselect() {
        registration.unregister();
        
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
    public MailboxPath getPath() throws MailboxException {
        return mailboxManager.getMailbox(mailboxId, mailboxSession).getMailboxPath();
    }

    @Override
    public MailboxId getMailboxId() {
        return mailboxId;
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
        applicableFlags = applicableFlags.ackUpdates();
    }

    @Override
    public synchronized NullableMessageSequenceNumber remove(MessageUid uid) {
        NullableMessageSequenceNumber result = msn(uid);
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

    
    
    @Override
    public synchronized void resetExpungedUids() {
        expungedUids.clear();
    }

    /**
     * Are flag changes from current session ignored?
     * 
     * @return true if any flag changes from current session will be ignored,
     *         false otherwise
     */
    public final synchronized boolean isSilentFlagChanges() {
        return silentFlagChanges;
    }

    /**
     * Sets whether changes from current session should be ignored.
     * 
     * @param silentFlagChanges
     *            true if any flag changes from current session should be
     *            ignored, false otherwise
     */
    public final synchronized void setSilentFlagChanges(boolean silentFlagChanges) {
        this.silentFlagChanges = silentFlagChanges;
    }

    /**
     * Has the size of the mailbox changed?
     * 
     * @return true if new messages have been added, false otherwise
     */

    @Override
    public final synchronized boolean isSizeChanged() {
        return sizeChanged;
    }

    /**
     * Is the mailbox deleted?
     * 
     * @return true when the mailbox has been deleted by another session, false
     *         otherwise
     */

    @Override
    public final synchronized boolean isDeletedByOtherSession() {
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

    @Override
    public synchronized Flags getApplicableFlags() {
        return applicableFlags.flags();
    }

    
    @Override
    public synchronized boolean hasNewApplicableFlags() {
        return applicableFlags.updated();
    }

    
    @Override
    public synchronized void resetNewApplicableFlags() {
        applicableFlags = applicableFlags.ackUpdates();
    }

    
    @Override
    public synchronized void event(Event event) {

        if (event instanceof MailboxEvent) {
            MailboxEvent mailboxEvent = (MailboxEvent) event;
            mailboxEvent(mailboxEvent);
        }
    }

    private void mailboxEvent(MailboxEvent mailboxEvent) {
        // Check if the event was for the mailbox we are observing
        if (mailboxEvent.getMailboxId().equals(getMailboxId())) {
            MailboxSession.SessionId eventSessionId = mailboxEvent.getSessionId();
            if (mailboxEvent instanceof MessageEvent) {
                final MessageEvent messageEvent = (MessageEvent) mailboxEvent;
                if (messageEvent instanceof Added) {
                    sizeChanged = true;
                    final Collection<MessageUid> uids = ((Added) mailboxEvent).getUids();
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
                                    MailboxId id = sm.getMailboxId();
                                    if (id != null && id.equals(mailboxEvent.getMailboxId())) {
                                        sm.addRecent(u.getUid());
                                    }
                                }
                            }
   
   
                        }
                    }

                    updateApplicableFlags((FlagsUpdated) messageEvent);

                } else if (messageEvent instanceof Expunged) {
                    expungedUids.addAll(messageEvent.getUids());
                    
                }
            } else if (mailboxEvent instanceof MailboxDeletion) {
                if (eventSessionId != sessionId) {
                    isDeletedByOtherSession = true;
                }
            }
        }
    }

    private void updateApplicableFlags(FlagsUpdated messageEvent) {
        Flags updatedFlags = applicableFlags.flags();
        int size = updatedFlags.getUserFlags().length;
        FlagsUpdated updatedF = messageEvent;
        List<UpdatedFlags> flags = updatedF.getUpdatedFlags();

        for (UpdatedFlags flag : flags) {
            updatedFlags.add(flag.getNewFlags());

        }

        // \RECENT is not a applicable flag in imap so remove it
        // from the list
        updatedFlags.remove(Flag.RECENT);

        boolean applicableFlagsChanged;
        if (size < updatedFlags.getUserFlags().length) {
            applicableFlagsChanged = true;
        } else {
            applicableFlagsChanged = false;
        }
        applicableFlags = ApplicableFlags.from(updatedFlags).update(applicableFlagsChanged);
    }

    @Override
    public synchronized NullableMessageSequenceNumber msn(MessageUid uid) {
        return uidMsnConverter.getMsn(uid);
    }

    @Override
    public synchronized Optional<MessageUid> uid(int msn) {
        if (msn == NO_SUCH_MESSAGE) {
            return Optional.empty();
        }

        return uidMsnConverter.getUid(msn);
    }

    
    @Override
    public synchronized long existsCount() {
        return uidMsnConverter.getNumMessage();
    }
}
