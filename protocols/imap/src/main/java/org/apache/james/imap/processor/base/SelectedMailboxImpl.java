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

import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.Predicates.instanceOf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.StampedLock;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.events.Registration;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.NullableMessageSequenceNumber;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.FlagsUpdated;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.events.MailboxEvents.MailboxEvent;
import org.apache.james.mailbox.events.MailboxEvents.MessageEvent;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link SelectedMailbox}
 */
public class SelectedMailboxImpl implements SelectedMailbox, EventListener {


    private static final Void VOID = null;
    private static final Flag UNINTERESTING_FLAGS = Flag.RECENT;

    @VisibleForTesting
    static class ApplicableFlags {
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

        public ApplicableFlags updateWithNewFlags(Flags newFlags) {
            Flags updatedFlags = flags();
            int size = updatedFlags.getUserFlags().length;

            updatedFlags.add(newFlags);
            // \RECENT is not a applicable flag in imap so remove it
            // from the list
            updatedFlags.remove(Flag.RECENT);

            boolean applicableFlagsChanged = size < updatedFlags.getUserFlags().length;
            return new ApplicableFlags(updatedFlags, applicableFlagsChanged);
        }
    }

    private final AtomicReference<Registration> registration = new AtomicReference<>();
    private final MailboxManager mailboxManager;
    private final MessageManager messageManager;
    private final MailboxId mailboxId;
    private final EventBus eventBus;
    private final ImapSession session;
    private final MailboxSession.SessionId sessionId;
    private final MailboxSession mailboxSession;
    private final UidMsnConverter uidMsnConverter;
    private final Set<MessageUid> recentUids = new TreeSet<>();
    private final Set<MessageUid> flagUpdateUids = new TreeSet<>();
    private final Set<MessageUid> expungedUids = new TreeSet<>();
    private final StampedLock applicableFlagsLock = new StampedLock();
    private final AtomicReference<EventListener> idleEventListener = new AtomicReference<>();
    private boolean recentUidRemoved = false;
    private boolean isDeletedByOtherSession = false;
    private boolean sizeChanged = false;
    private boolean silentFlagChanges = false;
    private ApplicableFlags applicableFlags = ApplicableFlags.from(new Flags());

    public SelectedMailboxImpl(MailboxManager mailboxManager, EventBus eventBus, ImapSession session, MessageManager messageManager) {
        this.eventBus = eventBus;
        this.session = session;
        this.sessionId = session.getMailboxSession().getSessionId();
        this.mailboxManager = mailboxManager;
        this.messageManager = messageManager;
        this.mailboxSession = session.getMailboxSession();
        this.uidMsnConverter = new UidMsnConverter();
        this.mailboxId = messageManager.getId();
    }

    public Mono<Void> finishInit() throws MailboxException {
        // Ignore events from our session
        setSilentFlagChanges(true);

        return Mono.from(eventBus.register(this, new MailboxIdRegistrationKey(mailboxId)))
                .doOnNext(this.registration::set)
            .then(Mono.using(applicableFlagsLock::writeLock,
                stamp -> messageManager.getApplicableFlagsReactive(mailboxSession)
                    .flatMap(flags -> Mono.fromRunnable(() -> applicableFlags = applicableFlags.updateWithNewFlags(flags))),
                applicableFlagsLock::unlockWrite)
                .then())
            .then(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.all()), mailboxSession))
                .collect(ImmutableList.toImmutableList())
                .doOnNext(uidMsnConverter::addAll))
            .then();
    }

    @Override
    public void registerIdle(EventListener idle) {
        idleEventListener.set(idle);
    }

    @Override
    public void unregisterIdle() {
        idleEventListener.set(null);
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
        registration.get().unregister();
        
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
        long stamp = applicableFlagsLock.writeLock();
        applicableFlags = applicableFlags.ackUpdates();
        applicableFlagsLock.unlockWrite(stamp);
    }

    @Override
    public synchronized NullableMessageSequenceNumber remove(MessageUid uid) {
        NullableMessageSequenceNumber result = msn(uid);
        uidMsnConverter.remove(uid);
        return result;
    }

    private boolean interestingFlags(UpdatedFlags updated) {
        boolean result;
        final Iterator<Flags.Flag> it = updated.modifiedSystemFlags().iterator();
        if (it.hasNext()) {
            final Flags.Flag flag = it.next();
            result = !flag.equals(UNINTERESTING_FLAGS);
        } else {
            result = false;
        }
        // See if we need to check the user flags
        if (!result) {
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
        return ImmutableSortedSet.copyOf(flagUpdateUids);
    }

    @Override
    public synchronized Collection<MessageUid> expungedUids() {
        // copy the TreeSet to fix possible
        // java.util.ConcurrentModificationException
        // See IMAP-278
        return ImmutableSortedSet.copyOf(expungedUids);
    }

    @Override
    public Flags getApplicableFlags() {
        return applicableFlags.flags();
    }

    
    @Override
    public boolean hasNewApplicableFlags() {
        return applicableFlags.updated();
    }

    
    @Override
    public synchronized void resetNewApplicableFlags() {
        long stamp = applicableFlagsLock.writeLock();
        applicableFlags = applicableFlags.ackUpdates();
        applicableFlagsLock.unlockWrite(stamp);
    }

    
    @Override
    public synchronized void event(Event event) {
        if (event instanceof MailboxEvent) {
            MailboxEvent mailboxEvent = (MailboxEvent) event;
            mailboxEvent(mailboxEvent);
        }
        Optional.ofNullable(idleEventListener.get())
            .ifPresent(Throwing.<EventListener>consumer(listener -> listener.event(event)).sneakyThrow());
    }

    private void mailboxEvent(MailboxEvent mailboxEvent) {
        // Check if the event was for the mailbox we are observing
        if (mailboxEvent.getMailboxId().equals(getMailboxId())) {
            Match(mailboxEvent).of(
                Case($(instanceOf(Added.class)),
                    this::handleAddition),
                Case($(instanceOf(FlagsUpdated.class)),
                    this::handleFlagsUpdates),
                Case($(instanceOf(Expunged.class)),
                    this::handleMailboxExpunge),
                Case($(instanceOf(MailboxDeletion.class)),
                    this::handleMailboxDeletion),
                Case($(), VOID)
            );
        }
    }

    private Void handleMailboxDeletion(MailboxDeletion mailboxDeletion) {
        if (mailboxDeletion.getSessionId() != sessionId) {
            isDeletedByOtherSession = true;
        }
        return VOID;
    }

    private Void handleMailboxExpunge(MessageEvent messageEvent) {
        expungedUids.addAll(messageEvent.getUids());
        return VOID;
    }

    private Void handleFlagsUpdates(FlagsUpdated updated) {
        List<UpdatedFlags> uFlags = updated.getUpdatedFlags();
        if (sessionId != updated.getSessionId() || !silentFlagChanges) {

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
                Iterator<Flag> flags = u.modifiedSystemFlags().iterator();

                while (flags.hasNext()) {
                    if (Flag.RECENT.equals(flags.next())) {
                        MailboxId id = sm.getMailboxId();
                        if (id != null && id.equals(updated.getMailboxId())) {
                            sm.addRecent(u.getUid());
                        }
                    }
                }
            }
        }
        long stamp = applicableFlagsLock.writeLock();
        applicableFlags = updateApplicableFlags(applicableFlags, updated);
        applicableFlagsLock.unlock(stamp);
        return VOID;
    }

    private Void handleAddition(Added added) {
        sizeChanged = true;
        SelectedMailbox sm = session.getSelected();
        for (MessageUid uid : added.getUids()) {
            uidMsnConverter.addUid(uid);
            if (sm != null) {
                sm.addRecent(uid);
            }
        }
        return VOID;
    }

    @VisibleForTesting
    static ApplicableFlags updateApplicableFlags(ApplicableFlags applicableFlags, FlagsUpdated flagsUpdated) {
        Flags updatedFlags = mergeAllNewFlags(flagsUpdated);
        return applicableFlags.updateWithNewFlags(updatedFlags);
    }

    private static Flags mergeAllNewFlags(FlagsUpdated flagsUpdated) {
        List<UpdatedFlags> flags = flagsUpdated.getUpdatedFlags();
        FlagsBuilder builder = FlagsBuilder.builder();
        flags.stream().map(UpdatedFlags::getNewFlags).forEach(builder::add);
        return builder.build();
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
