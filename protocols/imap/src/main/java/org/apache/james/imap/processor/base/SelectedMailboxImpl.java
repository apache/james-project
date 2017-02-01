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
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.UpdatedFlags;

import com.google.common.base.Optional;

/**
 * Default implementation of {@link SelectedMailbox}
 */
public class SelectedMailboxImpl implements SelectedMailbox, MailboxListener{

    private final Set<MessageUid> recentUids = new TreeSet<MessageUid>();

    private boolean recentUidRemoved = false;

    private final MailboxManager mailboxManager;

    private MailboxPath path;

    private final ImapSession session;
    

    private final static Flags FLAGS = new Flags();
    static {
        FLAGS.add(Flags.Flag.ANSWERED);
        FLAGS.add(Flags.Flag.DELETED);
        FLAGS.add(Flags.Flag.DRAFT);
        FLAGS.add(Flags.Flag.FLAGGED);
        FLAGS.add(Flags.Flag.SEEN);
    }
    
    private final long sessionId;
    private final Set<MessageUid> flagUpdateUids = new TreeSet<MessageUid>();
    private final Flags.Flag uninterestingFlag = Flags.Flag.RECENT;
    private final Set<MessageUid> expungedUids = new TreeSet<MessageUid>();

    private boolean isDeletedByOtherSession = false;
    private boolean sizeChanged = false;
    private boolean silentFlagChanges = false;
    private final Flags applicableFlags = new Flags(FLAGS);

    private boolean applicableFlagsChanged;
    
    private final SortedMap<Integer, MessageUid> msnToUid =new TreeMap<Integer, MessageUid>();

    private final SortedMap<MessageUid, Integer> uidToMsn = new TreeMap<MessageUid, Integer>();

    private MessageUid highestUid = MessageUid.MIN_VALUE;

    private int highestMsn = 0;
    
    public SelectedMailboxImpl(MailboxManager mailboxManager, ImapSession session, MailboxPath path) throws MailboxException {
        this.session = session;
        this.sessionId = ImapSessionUtils.getMailboxSession(session).getSessionId();
        this.mailboxManager = mailboxManager;
        
        // Ignore events from our session
        setSilentFlagChanges(true);
        this.path = path;
        init();
    }

    @Override
    public ListenerType getType() {
        return ListenerType.MAILBOX;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return ExecutionMode.SYNCHRONOUS;
    }

    private void init() throws MailboxException {
        MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);
        
        mailboxManager.addListener(path, this, mailboxSession);

        MessageResultIterator messages = mailboxManager.getMailbox(path, mailboxSession).getMessages(MessageRange.all(), FetchGroupImpl.MINIMAL, mailboxSession);
        synchronized (this) {
            while(messages.hasNext()) {
                MessageResult mr = messages.next();
                applicableFlags.add(mr.getFlags());
                add(mr.getUid());
            }
            
          
            // \RECENT is not a applicable flag in imap so remove it from the list
            applicableFlags.remove(Flags.Flag.RECENT);
        }
       
    }

    private void add(int msn, MessageUid uid) {
        if (uid.compareTo(highestUid) > 0) {
            highestUid = uid;
        }
        msnToUid.put(msn, uid);
        uidToMsn.put(uid, msn);
    }

    /**
     * Expunge the message with the given uid
     */
    private void expunge(MessageUid uid) {
        final int msn = msn(uid);
        remove(msn, uid);
        final List<Integer> renumberMsns = new ArrayList<Integer>(msnToUid.tailMap(msn + 1).keySet());
        for (Integer msnInteger : renumberMsns) {
            int aMsn = msnInteger.intValue();
            Optional<MessageUid> aUid = uid(aMsn);
            if (aUid.isPresent()) {
                remove(aMsn, aUid.get());
                add(aMsn - 1, aUid.get());
            }
        }
        highestMsn--;
    }

    private void remove(int msn, MessageUid uid) {
        uidToMsn.remove(uid);
        msnToUid.remove(msn);
    }

    /**
     * Add the give uid
     * 
     * @param uid
     */
    private void add(MessageUid uid) {
        if (!uidToMsn.containsKey(uid)) {
            highestMsn++;
            add(highestMsn, uid);
        }
    }

    @Override
    public synchronized Optional<MessageUid> getFirstUid() {
        if (uidToMsn.isEmpty()) {
            return Optional.absent();
        } else {
            return Optional.of(uidToMsn.firstKey());
        }
    }

    @Override
    public synchronized Optional<MessageUid> getLastUid() {
        if (uidToMsn.isEmpty()) {
            return Optional.absent();
        } else {
            return Optional.of(uidToMsn.lastKey());
        }
    }


    
    public synchronized void deselect() {
        MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);

        try {
            mailboxManager.removeListener(path, this, mailboxSession);
        } catch (MailboxException e) {
            if (session.getLog().isInfoEnabled()) {
                session.getLog().info("Unable to remove listener " + this + " from mailbox while closing it", e);
            }
        }
        
        uidToMsn.clear();
        msnToUid.clear();
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
        return new ArrayList<MessageUid>(recentUids);
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
        expunge(uid);
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
        return Collections.unmodifiableSet(new TreeSet<MessageUid>(flagUpdateUids));
        
    }

    @Override
    public synchronized Collection<MessageUid> expungedUids() {
        // copy the TreeSet to fix possible
        // java.util.ConcurrentModificationException
        // See IMAP-278
        return Collections.unmodifiableSet(new TreeSet<MessageUid>(expungedUids));
        
    }




    
    public synchronized Flags getApplicableFlags() {
        return applicableFlags;
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
                        add(uid);
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
        Integer msn = uidToMsn.get(uid);
        if (msn != null) {
            return msn.intValue();
        } else {
            return SelectedMailbox.NO_SUCH_MESSAGE;
        }
    }

    @Override
    public synchronized Optional<MessageUid> uid(int msn) {
        if (msn == -1) {
            return Optional.absent();
        }
        MessageUid uid = msnToUid.get(msn);
        if (uid != null) {
            return Optional.of(uid);
        } else {
            return Optional.absent();
        }
    }

    
    public synchronized long existsCount() {
        return uidToMsn.size();
    }
    

}
