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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.Flags;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.process.ImapLineHandler;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Headers;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLCommand;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLEntryKey;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLRight;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLRights;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResult.FetchGroup;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.MimeDescriptor;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.junit.Test;
import org.slf4j.Logger;

public class MailboxEventAnalyserTest {

    private static final long BASE_SESSION_ID = 99;

    
    private MailboxPath mailboxPath = new MailboxPath("namespace", "user", "name");
    private final MailboxManager mockManager = new MailboxManager() {
        
        
        public void removeListener(MailboxPath mailboxPath, MailboxListener listner, MailboxSession session) throws MailboxException {
            
        }
        
        
        public void removeGlobalListener(MailboxListener listner, MailboxSession session) throws MailboxException {
            
        }
        
        
        public void addListener(MailboxPath mailboxPath, MailboxListener listener, MailboxSession session) throws MailboxException {
            
        }
        
        
        public void addGlobalListener(MailboxListener listener, MailboxSession session) throws MailboxException {
            
        }
        
        
        public void startProcessingRequest(MailboxSession session) {
            
        }
        
        
        public void endProcessingRequest(MailboxSession session) {
            
        }
        
        
        public List<MailboxMetaData> search(MailboxQuery expression, MailboxSession session) throws MailboxException {
            throw new UnsupportedOperationException("Not implemented");

        }
        
        
        public void renameMailbox(MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
            throw new UnsupportedOperationException("Not implemented");
            
        }
        
        
        public boolean mailboxExists(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
            throw new UnsupportedOperationException("Not implemented");

        }
        
        
        public void logout(MailboxSession session, boolean force) throws MailboxException {
            throw new UnsupportedOperationException("Not implemented");
            
        }
        
        
        public MailboxSession login(String userid, String passwd, Logger log) throws BadCredentialsException, MailboxException {
            throw new UnsupportedOperationException("Not implemented");

        }
        
        
        public List<MailboxPath> list(MailboxSession session) throws MailboxException {
            throw new UnsupportedOperationException("Not implemented");
        }
        
        
        public MessageManager getMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
            return new MessageManager() {

                
                public long getMessageCount(MailboxSession mailboxSession) throws MailboxException {
                    return 1;
                }

                
                public boolean isWriteable(MailboxSession session) {
                    return false;
                }

                
                public boolean isModSeqPermanent(MailboxSession session) {
                    return false;
                }

                
                public Iterator<Long> search(SearchQuery searchQuery, MailboxSession mailboxSession) throws MailboxException {
                    throw new UnsupportedOperationException("Not implemented");

                }

                
                public Iterator<Long> expunge(MessageRange set, MailboxSession mailboxSession) throws MailboxException {
                    throw new UnsupportedOperationException("Not implemented");

                }

                
                public Map<Long, Flags> setFlags(Flags flags, FlagsUpdateMode mode, MessageRange set, MailboxSession mailboxSession) throws MailboxException {
                    throw new UnsupportedOperationException("Not implemented");

                }

                
                public long appendMessage(InputStream msgIn, Date internalDate, MailboxSession mailboxSession, boolean isRecent, Flags flags) throws MailboxException {
                    throw new UnsupportedOperationException("Not implemented");

                }

                
                public MessageResultIterator getMessages(MessageRange set, FetchGroup fetchGroup, MailboxSession mailboxSession) throws MailboxException {
                    return new MessageResultIterator() {
                        boolean done = false;
                        
                        public void remove() {
                            throw new UnsupportedOperationException("Not implemented");
                        }
                        
                        
                        public MessageResult next() {
                            done = true;
                            return new MessageResult() {

                                
                                public int compareTo(MessageResult o) {
                                    return 0;
                                }

                                
                                public long getUid() {
                                    return 1;
                                }

                                
                                public long getModSeq() {
                                    return 0;
                                }

                                
                                public Flags getFlags() {
                                    return new Flags();
                                }

                                
                                public long getSize() {
                                    return 0;
                                }

                                
                                public Date getInternalDate() {
                                    throw new UnsupportedOperationException("Not implemented");

                                }

                                
                                public MimeDescriptor getMimeDescriptor() throws MailboxException {
                                    throw new UnsupportedOperationException("Not implemented");
                                }

                                
                                public Iterator<Header> iterateHeaders(MimePath path) throws MailboxException {
                                    throw new UnsupportedOperationException("Not implemented");

                                }

                                
                                public Iterator<Header> iterateMimeHeaders(MimePath path) throws MailboxException {
                                    throw new UnsupportedOperationException("Not implemented");
                                }

                                
                                public Content getFullContent() throws MailboxException, IOException {
                                    throw new UnsupportedOperationException("Not implemented");

                                }

                                
                                public Content getFullContent(MimePath path) throws MailboxException {
                                    throw new UnsupportedOperationException("Not implemented");

                                }

                                
                                public Content getBody() throws MailboxException, IOException {
                                    throw new UnsupportedOperationException("Not implemented");

                                }

                                
                                public Content getBody(MimePath path) throws MailboxException {
                                    throw new UnsupportedOperationException("Not implemented");

                                }

                                
                                public Content getMimeBody(MimePath path) throws MailboxException {
                                    throw new UnsupportedOperationException("Not implemented");

                                }

                                
                                public Headers getHeaders() throws MailboxException {
                                    throw new UnsupportedOperationException("Not implemented");

                                }
                                
                            };
                        }
                        
                        
                        public boolean hasNext() {
                            return !done;
                        }
                        
                        
                        public MailboxException getException() {
                            return null;
                        }
                    };
                }

                
                public MetaData getMetaData(boolean resetRecent, MailboxSession mailboxSession, org.apache.james.mailbox.MessageManager.MetaData.FetchGroup fetchGroup) throws MailboxException {
                    throw new UnsupportedOperationException("Not implemented");

                }
            };
        }
        
        
        public char getDelimiter() {
            return '.';
        }
        
        
        public void deleteMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
            throw new UnsupportedOperationException("Not implemented");

        }
        
        
        public MailboxSession createSystemSession(String userName, Logger log) throws BadCredentialsException, MailboxException {
            throw new UnsupportedOperationException("Not implemented");
        }
        
        
        public void createMailbox(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
            throw new UnsupportedOperationException("Not implemented");
            
        }
        
        
        public List<MessageRange> copyMessages(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
            throw new UnsupportedOperationException("Not implemented");

        }
        public List<MessageRange> moveMessages(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
            throw new UnsupportedOperationException("Not implemented");

        }

        public boolean hasRight(MailboxPath mailboxPath, MailboxACLRight mailboxACLRight, MailboxSession mailboxSession) throws MailboxException {
            throw new NotImplementedException("Not implemented");
        }

        public MailboxACLRights myRights(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
            throw new NotImplementedException("Not implemented");
        }

        public MailboxACLRights[] listRigths(MailboxPath mailboxPath, MailboxACLEntryKey mailboxACLEntryKey, MailboxSession mailboxSession) throws MailboxException {
            throw new NotImplementedException("Not implemented");
        }

        public void setRights(MailboxPath mailboxPath,
                MailboxACLCommand mailboxACLCommand, MailboxSession session)
                throws MailboxException {
            throw new NotImplementedException("Not implemented");
        }
    };
    
    private final class MyMailboxSession implements MailboxSession {
        private final long sessionId;

        public MyMailboxSession(long sessionId) {
            this.sessionId = sessionId;
        }

        /**
         * @see org.apache.james.mailbox.mock.MockMailboxSession#getSessionId()
         */
        public long getSessionId() {
            return sessionId;
        }

        public void close() {            
        }

        public Map<Object, Object> getAttributes() {
            return null;
        }

        public Logger getLog() {
            return null;
        }

        public String getOtherUsersSpace() {
            return null;
        }

        public char getPathDelimiter() {
            return 0;
        }

        public String getPersonalSpace() {
            return null;
        }

        public Collection<String> getSharedSpaces() {
            return null;
        }

        public User getUser() {
            return null;
        }

        public boolean isOpen() {
            return false;
        }

        public SessionType getType() {
            return SessionType.System;
        }
        
        
    }
    
    private class MyImapSession implements ImapSession{
        private final MailboxSession mSession;

        public MyImapSession(MailboxSession mSession) {
            this.mSession = mSession;
        }
        
        public boolean supportStartTLS() {
            return false;
        }
        
        public boolean startTLS() {
            return false;
        }
        
        public boolean startCompression() {
            return false;
        }
        
        public void setAttribute(String key, Object value) {            
        }
        
        public void selected(SelectedMailbox mailbox) {
            
        }
        
        public void pushLineHandler(ImapLineHandler lineHandler) {
            
        }
        
        public void popLineHandler() {
            
        }
        
        public void logout() {
            
        }
        
        public boolean isCompressionSupported() {
            return false;
        }
        
        public ImapSessionState getState() {
            return ImapSessionState.AUTHENTICATED;
        }
        
        public SelectedMailbox getSelected() {
            return null;
        }
        
        public Logger getLog() {
            return null;
        }
        
        public Object getAttribute(String key) {
            if (key.equals(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY)) {
                return mSession;
            }
            return null;
        }
        
        public void deselect() {
            
        }
        
        public void authenticated() {
            
        }

        public boolean isPlainAuthDisallowed() {
            return false;
        }

        public boolean isTLSActive() {
            return false;
        }

        public boolean supportMultipleNamespaces() {
            return false;
        }

        public boolean isCompressionActive() {
            return false;
        }
    };
    

    @Test
    public void testShouldBeNoSizeChangeOnOtherEvent() throws Exception {
        MyMailboxSession mSession = new MyMailboxSession(0);
        
        MyImapSession imapsession = new MyImapSession(mSession);
        
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);

        final MailboxListener.Event event = new MailboxListener.Event(mSession, mailboxPath) {};
      
        analyser.event(event);
        assertFalse(analyser.isSizeChanged());
    }

    @Test
    public void testShouldBeNoSizeChangeOnAdded() throws Exception {
        MyMailboxSession mSession = new MyMailboxSession(0);
        
        MyImapSession imapsession = new MyImapSession(mSession);
        
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);
        
        analyser.event(new FakeMailboxListenerAdded(mSession, Arrays.asList(11L), mailboxPath));
        assertTrue(analyser.isSizeChanged());
    }

    @Test
    public void testShouldNoSizeChangeAfterReset() throws Exception {
        MyMailboxSession mSession = new MyMailboxSession(99);
        
        MyImapSession imapsession = new MyImapSession(mSession);
        
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);
        
        analyser.event(new FakeMailboxListenerAdded(mSession,  Arrays.asList(11L), mailboxPath));
        analyser.resetEvents();
        assertFalse(analyser.isSizeChanged());
    }

    @Test
    public void testShouldNotSetUidWhenNoSystemFlagChange() throws Exception {
        MyMailboxSession mSession = new MyMailboxSession(11);
        
        MyImapSession imapsession = new MyImapSession(mSession);
        
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);
        
        final FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(
                mSession,  Arrays.asList(90L),  Arrays.asList(new UpdatedFlags(90, -1, new Flags(), new Flags())), mailboxPath);
        analyser.event(update);
        assertNotNull(analyser.flagUpdateUids());
        assertFalse(analyser.flagUpdateUids().iterator().hasNext());
    }

    @Test
    public void testShouldSetUidWhenSystemFlagChange() throws Exception {
        final long uid = 900L;
        MyMailboxSession mSession = new MyMailboxSession(11);
        
        MyImapSession imapsession = new MyImapSession(mSession);
        
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);
        
        final FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(
                new MyMailboxSession(41), Arrays.asList(uid), Arrays.asList(new UpdatedFlags(uid, -1, new Flags(), new Flags(Flags.Flag.ANSWERED))), mailboxPath);
        analyser.event(update);
        final Iterator<Long> iterator = analyser.flagUpdateUids().iterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(new Long(uid), iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldClearFlagUidsUponReset() throws Exception {
        final long uid = 900L;
        MyMailboxSession mSession = new MyMailboxSession(11);
        MyImapSession imapsession = new MyImapSession(mSession);
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);
        
        final FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(
                mSession, Arrays.asList(uid), Arrays.asList(new UpdatedFlags(uid, -1, new Flags(), new Flags(Flags.Flag.ANSWERED))), mailboxPath);
        analyser.event(update);
        analyser.event(update);
        analyser.deselect();
        assertNotNull(analyser.flagUpdateUids());
        assertFalse(analyser.flagUpdateUids().iterator().hasNext());
    }

    @Test
    public void testShouldSetUidWhenSystemFlagChangeDifferentSessionInSilentMode()
            throws Exception {
        final long uid = 900L;
        
        MyMailboxSession mSession = new MyMailboxSession(11);
        MyImapSession imapsession = new MyImapSession(mSession);
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);
        
        final FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(
                new MyMailboxSession(BASE_SESSION_ID), Arrays.asList(uid), Arrays.asList(new UpdatedFlags(uid, -1, new Flags(), new Flags(Flags.Flag.ANSWERED))), mailboxPath);
        analyser.event(update);
        analyser.setSilentFlagChanges(true);
        analyser.event(update);
        final Iterator<Long> iterator = analyser.flagUpdateUids().iterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(new Long(uid), iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldNotSetUidWhenSystemFlagChangeSameSessionInSilentMode()
            throws Exception {
        MyMailboxSession mSession = new MyMailboxSession(BASE_SESSION_ID);
        MyImapSession imapsession = new MyImapSession(mSession);
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);
        
        
        final FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(
                mSession, Arrays.asList(345L), Arrays.asList(new UpdatedFlags(345, -1, new Flags(), new Flags())), mailboxPath);
        analyser.event(update);
        analyser.setSilentFlagChanges(true);
        analyser.event(update);
        final Iterator<Long> iterator = analyser.flagUpdateUids().iterator();
        assertNotNull(iterator);
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldNotSetUidWhenOnlyRecentFlagUpdated() throws Exception {
        MyMailboxSession mSession = new MyMailboxSession(BASE_SESSION_ID);
        MyImapSession imapsession = new MyImapSession(mSession);
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);
        
        
        final FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(
                mSession, Arrays.asList(886L), Arrays.asList(new UpdatedFlags(886, -1, new Flags(), new Flags(Flags.Flag.RECENT))), mailboxPath);
        analyser.event(update);
        final Iterator<Long> iterator = analyser.flagUpdateUids().iterator();
        assertNotNull(iterator);
        assertFalse(iterator.hasNext());
    }

    /**
     * @see org.apache.james.mailbox.MessageManager#myRights(org.apache.james.mailbox.MailboxSession)
     */
    public MailboxACLRights myRights(MailboxSession session) throws MailboxException {
        return null;
    }
}
