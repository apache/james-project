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
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Headers;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.ACLCommand;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResult.FetchGroup;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.MimeDescriptor;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class MailboxEventAnalyserTest {

    private static final long BASE_SESSION_ID = 99;
    public static final MessageUid MESSAGE_UID = MessageUid.of(1);


    private MailboxPath mailboxPath = new MailboxPath("namespace", "user", "name");
    private final MailboxManager mockManager = new MailboxManager() {

        @Override
        public EnumSet<MailboxCapabilities> getSupportedMailboxCapabilities() {
            return EnumSet.noneOf(MailboxCapabilities.class);
        }

        @Override
        public EnumSet<MessageCapabilities> getSupportedMessageCapabilities() {
            return EnumSet.noneOf(MessageCapabilities.class);
        }
        
        @Override
        public EnumSet<SearchCapabilities> getSupportedSearchCapabilities() {
            return EnumSet.noneOf(SearchCapabilities.class);
        }
        
        public boolean hasCapability(MailboxCapabilities capability) {
            return false;
        }

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
        
        
        public MailboxSession login(String userid, String passwd) throws MailboxException {
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

                @Override
                public MailboxCounters getMailboxCounters(MailboxSession mailboxSession) throws MailboxException {
                    throw new UnsupportedOperationException("Not implemented");
                }

                public boolean isWriteable(MailboxSession session) {
                    return false;
                }

                public boolean isModSeqPermanent(MailboxSession session) {
                    return false;
                }

                @Override
                public Iterator<MessageUid> search(SearchQuery searchQuery, MailboxSession mailboxSession) throws MailboxException {
                    return ImmutableList.of(MESSAGE_UID).iterator();
                }

                @Override
                public Iterator<MessageUid> expunge(MessageRange set, MailboxSession mailboxSession) throws MailboxException {
                    throw new UnsupportedOperationException("Not implemented");
                }

                @Override
                public Map<MessageUid, Flags> setFlags(Flags flags, FlagsUpdateMode mode, MessageRange set, MailboxSession mailboxSession) throws MailboxException {
                    throw new UnsupportedOperationException("Not implemented");
                }
                
                public ComposedMessageId appendMessage(InputStream msgIn, Date internalDate, MailboxSession mailboxSession, boolean isRecent, Flags flags) throws MailboxException {
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

                                @Override
                                public MailboxId getMailboxId() {
                                    return TestId.of(36);
                                }

                                public int compareTo(MessageResult o) {
                                    return 0;
                                }

                                @Override
                                public MessageUid getUid() {
                                    return MESSAGE_UID;
                                }

                                @Override
                                public MessageId getMessageId() {
                                    return new DefaultMessageId();
                                };
                                
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
                                
                                public List<MessageAttachment> getAttachments() {
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
                
                public MailboxId getId() {
                    return null;
                }
                
                public MailboxPath getMailboxPath() {
                    return null;
                }

                @Override
                public Flags getApplicableFlags(MailboxSession session) throws MailboxException {
                    return new Flags();
                }
            };
        }

        public MessageManager getMailbox(MailboxId mailboxId, MailboxSession session) throws MailboxException {
            return getMailbox((MailboxPath)null, session);
        }
        
        public char getDelimiter() {
            return '.';
        }

        public void deleteMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
            throw new UnsupportedOperationException("Not implemented");
        }
        
        public MailboxSession createSystemSession(String userName) throws MailboxException {
            throw new UnsupportedOperationException("Not implemented");
        }
        
        public Optional<MailboxId> createMailbox(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
            throw new UnsupportedOperationException("Not implemented");
        }
        
        public List<MessageRange> copyMessages(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
            throw new UnsupportedOperationException("Not implemented");
        }

        public java.util.List<MessageRange> copyMessages(MessageRange set, MailboxId from, MailboxId to, MailboxSession session) throws MailboxException {
            throw new UnsupportedOperationException("Not implemented");
        }
        
        public List<MessageRange> moveMessages(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
            throw new UnsupportedOperationException("Not implemented");
        }

        public boolean hasRight(MailboxPath mailboxPath, Right mailboxACLRight, MailboxSession mailboxSession) throws MailboxException {
            throw new NotImplementedException("Not implemented");
        }

        public Rfc4314Rights myRights(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
            throw new NotImplementedException("Not implemented");
        }

        @Override
        public Rfc4314Rights myRights(MailboxId mailboxId, MailboxSession session) throws MailboxException {
            throw new NotImplementedException("Not implemented");
        }

        public Rfc4314Rights[] listRigths(MailboxPath mailboxPath, EntryKey mailboxACLEntryKey, MailboxSession mailboxSession) throws MailboxException {
            throw new NotImplementedException("Not implemented");
        }

        public void applyRightsCommand(MailboxPath mailboxPath,
                                       ACLCommand mailboxACLCommand, MailboxSession session)
                throws MailboxException {
            throw new NotImplementedException("Not implemented");
        }

        @Override
        public void setRights(MailboxPath mailboxPath, MailboxACL mailboxACL, MailboxSession session) throws MailboxException {
            throw new NotImplementedException("Not implemented");
        }

        @Override
        public void setRights(MailboxId mailboxId, MailboxACL mailboxACL, MailboxSession session) throws MailboxException {
            throw new NotImplementedException("Not implemented");
        }

        @Override
        public List<MailboxAnnotation> getAllAnnotations(MailboxPath mailboxPath, MailboxSession session)
                throws MailboxException {
            return null;
        }

        @Override
        public List<MailboxAnnotation> getAnnotationsByKeys(MailboxPath mailboxPath, MailboxSession session,
                Set<MailboxAnnotationKey> keys) throws MailboxException {
            return null;
        }

        @Override
        public void updateAnnotations(MailboxPath mailboxPath, MailboxSession session,
                List<MailboxAnnotation> mailboxAnnotations) throws MailboxException {
        }

        @Override
        public List<MessageId> search(MultimailboxesSearchQuery expression, MailboxSession session, long limit) throws MailboxException {
            return null;
        }

        @Override
        public List<MailboxAnnotation> getAnnotationsByKeysWithOneDepth(MailboxPath mailboxPath, MailboxSession session,
                Set<MailboxAnnotationKey> keys) throws MailboxException {
            return null;
        }

        @Override
        public List<MailboxAnnotation> getAnnotationsByKeysWithAllDepth(MailboxPath mailboxPath, MailboxSession session,
                Set<MailboxAnnotationKey> keys) throws MailboxException {
            return null;
        }

        @Override
        public boolean hasChildren(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
            return false;
        }

        @Override
        public MailboxSession loginAsOtherUser(String adminUserId, String passwd, String realUserId) throws BadCredentialsException, MailboxException {
            throw new UnsupportedOperationException("Not implemented");
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
    }


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
        
        analyser.event(new FakeMailboxListenerAdded(mSession, ImmutableList.of(MessageUid.of(11)), mailboxPath));
        assertTrue(analyser.isSizeChanged());
    }

    @Test
    public void testShouldNoSizeChangeAfterReset() throws Exception {
        MyMailboxSession mSession = new MyMailboxSession(99);
        
        MyImapSession imapsession = new MyImapSession(mSession);
        
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);
        
        analyser.event(new FakeMailboxListenerAdded(mSession,  ImmutableList.of(MessageUid.of(11)), mailboxPath));
        analyser.resetEvents();
        assertFalse(analyser.isSizeChanged());
    }

    @Test
    public void testShouldNotSetUidWhenNoSystemFlagChange() throws Exception {
        MyMailboxSession mSession = new MyMailboxSession(11);
        
        MyImapSession imapsession = new MyImapSession(mSession);
        
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);
        
        final FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(mSession,
            ImmutableList.of(MessageUid.of(90L)),
            ImmutableList.of(UpdatedFlags.builder()
                .uid(MessageUid.of(90))
                .modSeq(-1)
                .oldFlags(new Flags())
                .newFlags(new Flags())
                .build()),
            mailboxPath);
        analyser.event(update);
        assertNotNull(analyser.flagUpdateUids());
        assertFalse(analyser.flagUpdateUids().iterator().hasNext());
    }

    @Test
    public void testShouldSetUidWhenSystemFlagChange() throws Exception {
        final MessageUid uid = MessageUid.of(900);
        MyMailboxSession mSession = new MyMailboxSession(11);
        
        MyImapSession imapsession = new MyImapSession(mSession);
        
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);
        
        final FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(new MyMailboxSession(41),
            ImmutableList.of(uid),
            ImmutableList.of(UpdatedFlags.builder()
                .uid(uid)
                .modSeq(-1)
                .oldFlags(new Flags())
                .newFlags(new Flags(Flags.Flag.ANSWERED))
                .build()),
            mailboxPath);
        analyser.event(update);
        final Iterator<MessageUid> iterator = analyser.flagUpdateUids().iterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(uid, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldClearFlagUidsUponReset() throws Exception {
        final MessageUid uid = MessageUid.of(900);
        MyMailboxSession mSession = new MyMailboxSession(11);
        MyImapSession imapsession = new MyImapSession(mSession);
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);
        
        final FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(mSession,
            ImmutableList.of(uid),
            ImmutableList.of(UpdatedFlags.builder()
                .uid(uid)
                .modSeq(-1)
                .oldFlags(new Flags())
                .newFlags(new Flags(Flags.Flag.ANSWERED))
                .build()),
            mailboxPath);
        analyser.event(update);
        analyser.event(update);
        analyser.deselect();
        assertNotNull(analyser.flagUpdateUids());
        assertFalse(analyser.flagUpdateUids().iterator().hasNext());
    }

    @Test
    public void testShouldSetUidWhenSystemFlagChangeDifferentSessionInSilentMode()
            throws Exception {
        final MessageUid uid = MessageUid.of(900);
        
        MyMailboxSession mSession = new MyMailboxSession(11);
        MyImapSession imapsession = new MyImapSession(mSession);
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);
        
        final FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(new MyMailboxSession(BASE_SESSION_ID),
            ImmutableList.of(uid),
            ImmutableList.of(UpdatedFlags.builder()
                .uid(uid)
                .modSeq(-1)
                .oldFlags(new Flags())
                .newFlags(new Flags(Flags.Flag.ANSWERED))
                .build()),
            mailboxPath);
        analyser.event(update);
        analyser.setSilentFlagChanges(true);
        analyser.event(update);
        final Iterator<MessageUid> iterator = analyser.flagUpdateUids().iterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(uid, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldNotSetUidWhenSystemFlagChangeSameSessionInSilentMode()
            throws Exception {
        MyMailboxSession mSession = new MyMailboxSession(BASE_SESSION_ID);
        MyImapSession imapsession = new MyImapSession(mSession);
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);
        
        
        final FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(mSession,
            ImmutableList.of(MessageUid.of(345)),
            ImmutableList.of(UpdatedFlags.builder()
                .uid(MessageUid.of(345))
                .modSeq(-1)
                .oldFlags(new Flags())
                .newFlags(new Flags())
                .build()),
            mailboxPath);
        analyser.event(update);
        analyser.setSilentFlagChanges(true);
        analyser.event(update);
        final Iterator<MessageUid> iterator = analyser.flagUpdateUids().iterator();
        assertNotNull(iterator);
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldNotSetUidWhenOnlyRecentFlagUpdated() throws Exception {
        MyMailboxSession mSession = new MyMailboxSession(BASE_SESSION_ID);
        MyImapSession imapsession = new MyImapSession(mSession);
        SelectedMailboxImpl analyser = new SelectedMailboxImpl(mockManager, imapsession, mailboxPath);


        final FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(mSession,
            ImmutableList.of(MessageUid.of(886)),
            ImmutableList.of(UpdatedFlags.builder()
                .uid(MessageUid.of(886))
                .modSeq(-1)
                .oldFlags(new Flags())
                .newFlags(new Flags(Flags.Flag.RECENT))
                .build()),
            mailboxPath);
        analyser.event(update);
        final Iterator<MessageUid> iterator = analyser.flagUpdateUids().iterator();
        assertNotNull(iterator);
        assertFalse(iterator.hasNext());
    }
}
