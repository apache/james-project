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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapProcessor.Responder;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.DeleteACLRequest;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSession.User;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MetaData;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * DeleteACLProcessor Test.
 * 
 * @author Peter Palaga
 */
public class DeleteACLProcessorTest {

    private static final String MAILBOX_NAME = ImapConstants.INBOX_NAME;
    private static final String USER_1 = "user1";

    private ImapSession imapSession;
    private MailboxManager mailboxManager;
    private MailboxSession mailboxSession;
    private MetaData metaData;
    private DeleteACLRequest deleteACLRequest;
    private DeleteACLProcessor subject;
    private EntryKey user1Key;
    private MailboxPath path;
    private Responder responder;
    private ArgumentCaptor<ImapResponseMessage> argumentCaptor;

    @Before
    public void setUp() throws Exception {
        path = MailboxPath.forUser(USER_1, MAILBOX_NAME);
        UnpooledStatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        mailboxManager = mock(MailboxManager.class);
        subject = new DeleteACLProcessor(mock(ImapProcessor.class), mailboxManager, statusResponseFactory, new NoopMetricFactory());
        imapSession = mock(ImapSession.class);
        mailboxSession = mock(MailboxSession.class);
        User user1 = mock(User.class);
        MessageManager messageManager = mock(MessageManager.class);
        metaData = mock(MetaData.class);
        responder = mock(Responder.class);

        when(imapSession.getAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY))
            .thenReturn(mailboxSession);
        when(imapSession.getState())
            .thenReturn(ImapSessionState.AUTHENTICATED);
        when(mailboxSession.getUser())
            .thenReturn(user1);
        when(user1.getUserName())
            .thenReturn(USER_1);
        when(messageManager.getMetaData(anyBoolean(), any(MailboxSession.class), any(FetchGroup.class)))
            .thenReturn(metaData);
        when(mailboxManager.getMailbox(any(MailboxPath.class), any(MailboxSession.class)))
            .thenReturn(messageManager);

        deleteACLRequest = new DeleteACLRequest("TAG",
            ImapCommand.anyStateCommand("Name"),
            MAILBOX_NAME,
            USER_1);

        user1Key = EntryKey.deserialize(USER_1);

        argumentCaptor = ArgumentCaptor.forClass(ImapResponseMessage.class);
    }
    
    @Test
    public void testNoListRight() throws Exception {
        when(mailboxManager.hasRight(path, MailboxACL.Right.Lookup, mailboxSession))
            .thenReturn(false);

        subject.doProcess(deleteACLRequest, responder, imapSession);

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }

    @Test
    public void testNoAdminRight() throws Exception {
        when(mailboxManager.hasRight(path, MailboxACL.Right.Lookup, mailboxSession))
            .thenReturn(true);
        when(mailboxManager.hasRight(path, MailboxACL.Right.Administer, mailboxSession))
            .thenReturn(false);

        subject.doProcess(deleteACLRequest, responder, imapSession);

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }

    @Test
    public void testNonExistentMailboxName() throws Exception {
        when(mailboxManager.getMailbox(any(MailboxPath.class), any(MailboxSession.class)))
            .thenThrow(new MailboxNotFoundException(""));

        subject.doProcess(deleteACLRequest, responder, imapSession);

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }

    
    @Test
    public void testDelete() throws MailboxException {
        MailboxACL acl = MailboxACL.OWNER_FULL_ACL;
        when(mailboxManager.hasRight(path, MailboxACL.Right.Lookup, mailboxSession))
            .thenReturn(true);
        when(mailboxManager.hasRight(path, MailboxACL.Right.Administer, mailboxSession))
            .thenReturn(true);
        when(metaData.getACL()).thenReturn(acl);

        subject.doProcess(deleteACLRequest, responder, imapSession);

        verify(mailboxManager).applyRightsCommand(path,
            MailboxACL.command().key(user1Key).noRights().asReplacement(),
            mailboxSession);

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.OK_RESPONSE_MATCHER::matches);
    }

}
