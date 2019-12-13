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

package org.apache.james.imap.processor;

import static org.apache.james.imap.ImapFixture.TAG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapProcessor.Responder;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.SetACLRequest;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MetaData;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.EditMode;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * SetACLProcessor Test.
 */
public class SetACLProcessorTest {

    private static final String MAILBOX_NAME = ImapConstants.INBOX_NAME;
    private static final Username USER_1 = Username.of("user1");
    private static final String SET_RIGHTS = "aw";
    private static final String UNSUPPORTED_RIGHT = "W";

    private FakeImapSession imapSession;
    private MailboxManager mailboxManager;
    private MailboxSession mailboxSession;
    private SetACLProcessor subject;
    private EntryKey user1Key;
    private MailboxPath path;
    private Responder responder;
    private ArgumentCaptor<ImapResponseMessage> argumentCaptor;
    private SetACLRequest replaceAclRequest;
    private Rfc4314Rights setRights;

    @Before
    public void setUp() throws Exception {
        path = MailboxPath.forUser(USER_1, MAILBOX_NAME);
        UnpooledStatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        mailboxManager = mock(MailboxManager.class);
        subject = new SetACLProcessor(mock(ImapProcessor.class), mailboxManager, statusResponseFactory, new NoopMetricFactory());
        imapSession = new FakeImapSession();
        mailboxSession = MailboxSessionUtil.create(USER_1);
        MessageManager messageManager = mock(MessageManager.class);
        MetaData metaData = mock(MetaData.class);
        responder = mock(Responder.class);

        argumentCaptor = ArgumentCaptor.forClass(ImapResponseMessage.class);

        imapSession.authenticated();
        imapSession.setMailboxSession(mailboxSession);
        when(messageManager.getMetaData(anyBoolean(), any(MailboxSession.class), any(FetchGroup.class)))
            .thenReturn(metaData);
        when(mailboxManager.getMailbox(any(MailboxPath.class), any(MailboxSession.class)))
            .thenReturn(messageManager);

        replaceAclRequest = new SetACLRequest(TAG, MAILBOX_NAME, USER_1.asString(), SET_RIGHTS);

        user1Key = EntryKey.deserialize(USER_1.asString());
        setRights = Rfc4314Rights.fromSerializedRfc4314Rights(SET_RIGHTS);
    }
    
    @Test
    public void testUnsupportedRight() throws Exception {
        SetACLRequest setACLRequest = new SetACLRequest(TAG, MAILBOX_NAME, USER_1.asString(), UNSUPPORTED_RIGHT);

        when(mailboxManager.hasRight(path, MailboxACL.Right.Lookup, mailboxSession))
            .thenReturn(false);

        subject.doProcess(setACLRequest, responder, imapSession);

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.BAD_RESPONSE_MATCHER::matches);
    }
    
    @Test
    public void testNoAdminRight() throws Exception {
        when(mailboxManager.hasRight(path, MailboxACL.Right.Lookup, mailboxSession))
            .thenReturn(true);
        when(mailboxManager.hasRight(path, MailboxACL.Right.Administer, mailboxSession))
            .thenReturn(false);

        subject.doProcess(replaceAclRequest, responder, imapSession);

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }
    
    @Test
    public void testInexistentMailboxName() throws Exception {
        when(mailboxManager.getMailbox(any(MailboxPath.class), any(MailboxSession.class)))
            .thenThrow(new MailboxNotFoundException(""));

        subject.doProcess(replaceAclRequest, responder, imapSession);

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }

    @Test
    public void testAddRights() throws Exception {
        testOp("+", EditMode.ADD);
    }

    @Test
    public void testRemoveRights() throws Exception {
        testOp("-", EditMode.REMOVE);
    }

    @Test
    public void testReplaceRights() throws Exception {
        testOp("", EditMode.REPLACE);
    }
    
    private void testOp(String prefix, EditMode editMode) throws MailboxException {
        when(mailboxManager.hasRight(path, MailboxACL.Right.Lookup, mailboxSession))
            .thenReturn(true);
        when(mailboxManager.hasRight(path, MailboxACL.Right.Administer, mailboxSession))
            .thenReturn(true);

        SetACLRequest r = new SetACLRequest(TAG, MAILBOX_NAME, USER_1.asString(), prefix + SET_RIGHTS);
        subject.doProcess(r, responder, imapSession);

        verify(mailboxManager).applyRightsCommand(path,
            MailboxACL.command().key(user1Key).rights(setRights).mode(editMode).build(),
            mailboxSession);

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.OK_RESPONSE_MATCHER::matches);
    }

}
