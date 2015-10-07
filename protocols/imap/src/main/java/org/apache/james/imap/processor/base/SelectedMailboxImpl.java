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
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.UpdatedFlags;

/**
 * Default implementation of {@link SelectedMailbox}
 */
public class SelectedMailboxImpl implements SelectedMailbox, MailboxListener{

    private final Set<Long> recentUids = new TreeSet<Long>();

    private boolean recentUidRemoved = false;

    private MailboxManager mailboxManager;

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
    private final Set<Long> flagUpdateUids = new TreeSet<Long>();;
    private final Flags.Flag uninterestingFlag = Flags.Flag.RECENT;
    private final Set<Long> expungedUids = new TreeSet<Long>();

    private boolean isDeletedByOtherSession = false;
    private boolean sizeChanged = false;
    private boolean silentFlagChanges = false;
    private final Flags applicableFlags = new Flags(FLAGS);

    private boolean applicableFlagsChanged;
    
    private final SortedMap<Integer, Long> msnToUid =new TreeMap<Integer, Long>();;

    private final SortedMap<Long, Integer> uidToMsn = new TreeMap<Long, Integer>();

    private long highestUid = 0;

    private int highestMsn = 0;
    
    public SelectedMailboxImpl(final MailboxManager mailboxManager, final ImapSession session, final MailboxPath path) throws MailboxException {
        this.session = session;
        this.sessionId = ImapSessionUtils.getMailboxSession(session).getSessionId();
        this.mailboxManager = mailboxManager;
        
        // Ignore events from our session
        setSilentFlagChanges(true);
        this.path = path;
        init();
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

    private void add(int msn, long uid) {
        if (uid > highestUid) {
            highestUid = uid;
        }
        msnToUid.put(msn, uid);
        uidToMsn.put(uid, msn);
    }

    /**
     * Expunge the message with the given uid
     * 
     * @param uid
     */
    private void expunge(final long uid) {
        final int msn = msn(uid);
        remove(msn, uid);
        final List<Integer> renumberMsns = new ArrayList<Integer>(msnToUid.tailMap(msn + 1).keySet());
        for (final Integer msnInteger : renumberMsns) {
            int aMsn = msnInteger.intValue();
            long aUid = uid(aMsn);
            remove(aMsn, aUid);
            add(aMsn - 1, aUid);
        }
        highestMsn--;
    }

    private void remove(int msn, long uid) {
        uidToMsn.remove(uid);
        msnToUid.remove(msn);
    }

    /**
     * Add the give uid
     * 
     * @param uid
     */
    private void add(long uid) {
        if (!uidToMsn.containsKey(uid)) {
            highestMsn++;
            add(highestMsn, uid);
        }
    }

    /**
     * @see org.apache.james.mailbox.MailboxListener#event(org.apache.james.mailbox.MailboxListener.Event)
     */


    /**
     * @see SelectedMailbox#getFirstUid()
     */
    public synchronized long getFirstUid() {
        if (uidToMsn.isEmpty()) {
            return -1;
        } else {
            return uidToMsn.firstKey();
        }
    }

    /**
     * @see SelectedMailbox#getLastUid()
     */
    public synchronized long getLastUid() {
        if (uidToMsn.isEmpty()) {
            return -1;
        } else {
            return uidToMsn.lastKey();
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

    /**
     * @see org.apache.james.imap.api.process.SelectedMailbox#removeRecent(long)
     */
    
    public synchronized  boolean removeRecent(long uid) {
        final boolean result = recentUids.remove(uid);
        if (result) {
            recentUidRemoved = true;
        }
        return result;
    }

    /**
     * @see org.apache.james.imap.api.process.SelectedMailbox#addRecent(long)
     */
    public synchronized boolean addRecent(long uid) {
        return recentUids.add(uid);
    }

    /**
     * @see org.apache.james.imap.api.process.SelectedMailbox#getRecent()
     */
    
    public synchronized Collection<Long> getRecent() {
        checkExpungedRecents();
        return new ArrayList<Long>(recentUids);
    }

    /**
     * @see org.apache.james.imap.api.process.SelectedMailbox#recentCount()
     */
    
    public synchronized int recentCount() {
        checkExpungedRecents();
        return recentUids.size();
    }

    /**
     * @see org.apache.james.imap.api.process.SelectedMailbox#getPath()
     */
    
    public synchronized MailboxPath getPath() {
        return path;
    }

    private void checkExpungedRecents() {
        for (final long uid : expungedUids()) {
            removeRecent(uid);
        }
    }

    /**
     * @see org.apache.james.imap.api.process.SelectedMailbox#isRecent(long)
     */
    
    public synchronized boolean isRecent(long uid) {
        return recentUids.contains(uid);
    }

    /**
     * @see
     * org.apache.james.imap.api.process.SelectedMailbox#isRecentUidRemoved()
     */
    
    public synchronized boolean isRecentUidRemoved() {
        return recentUidRemoved;
    }

    /**
     * @see
     * org.apache.james.imap.api.process.SelectedMailbox#resetRecentUidRemoved()
     */
    
    public synchronized void resetRecentUidRemoved() {
        recentUidRemoved = false;
    }

    /**
     * @see org.apache.james.imap.api.process.SelectedMailbox#resetEvents()
     */
    public synchronized void resetEvents() {
        sizeChanged = false;
        flagUpdateUids.clear();
        isDeletedByOtherSession = false;
        applicableFlagsChanged = false;
    }

    /**
     * @see
     * org.apache.james.imap.api.process.SelectedMailbox#remove(java.lang.Long)
     */
    
    public synchronized  int remove(Long uid) {
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
     * 
     * @return uids
     */
    
    public synchronized Collection<Long> flagUpdateUids() {
        // copy the TreeSet to fix possible
        // java.util.ConcurrentModificationException
        // See IMAP-278
        return Collections.unmodifiableSet(new TreeSet<Long>(flagUpdateUids));
        
    }

    /**
     * Return a unmodifiable {@link Collection} of uids that where expunged
     * 
     * @return uids
     */
    
    public synchronized Collection<Long> expungedUids() {
        // copy the TreeSet to fix possible
        // java.util.ConcurrentModificationException
        // See IMAP-278
        return Collections.unmodifiableSet(new TreeSet<Long>(expungedUids));
        
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
                // final List<Long> uids = messageEvent.getUids();
                if (messageEvent instanceof Added) {
                    sizeChanged = true;
                    final List<Long> uids = ((Added) event).getUids();
                    for (int i = 0; i < uids.size(); i++) {
                        add(uids.get(i));
                    }
                } else if (messageEvent instanceof FlagsUpdated) {
                    FlagsUpdated updated = (FlagsUpdated) messageEvent;
                    List<UpdatedFlags> uFlags = updated.getUpdatedFlags();
                    if (sessionId != eventSessionId || !silentFlagChanges) {

                        for (int i = 0; i < uFlags.size(); i++) {
                            UpdatedFlags u = uFlags.get(i);

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
                        for (int i = 0; i < uflags.size(); i++) {
                            UpdatedFlags u = uflags.get(i);
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

                    for (int i = 0; i < flags.size(); i++) {
                        applicableFlags.add(flags.get(i).getNewFlags());

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

    
    public synchronized int msn(long uid) {
        Integer msn = uidToMsn.get(uid);
        if (msn != null) {
            return msn.intValue();
        } else {
            return SelectedMailbox.NO_SUCH_MESSAGE;
        }
    }

    
    public synchronized long uid(int msn) {
        if (msn == -1) {
            return SelectedMailbox.NO_SUCH_MESSAGE;
        }
        Long uid = msnToUid.get(msn);
        if (uid != null) {
            return uid.longValue();
        } else {
            return SelectedMailbox.NO_SUCH_MESSAGE;
        }
    }

    
    public synchronized long existsCount() {
        return uidToMsn.size();
    }
    

}
