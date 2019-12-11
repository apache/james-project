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
import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapProcessor.Responder;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.GetACLRequest;
import org.apache.james.imap.message.response.ACLResponse;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MetaData;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * GetACLProcessor Test.
 * 
 */
public class GetACLProcessorTest {

    private static final String MAILBOX_NAME = ImapConstants.INBOX_NAME;
    private static final Username USER_1 = Username.of("user1");

    private FakeImapSession imapSession;
    private MailboxManager mailboxManager;
    private MailboxSession mailboxSession;
    private MetaData metaData;
    private GetACLRequest getACLRequest;
    private GetACLProcessor subject;
    private MailboxPath path;
    private ArgumentCaptor<ImapResponseMessage> argumentCaptor;
    private Responder responder;

    @Before
    public void setUp() throws Exception {
        path = MailboxPath.forUser(USER_1, MAILBOX_NAME);
        UnpooledStatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        mailboxManager = mock(MailboxManager.class);
        subject = new GetACLProcessor(mock(ImapProcessor.class), mailboxManager, statusResponseFactory, new NoopMetricFactory());
        imapSession = new FakeImapSession();
        mailboxSession = MailboxSessionUtil.create(USER_1);
        MessageManager messageManager = mock(MessageManager.class);
        metaData = mock(MetaData.class);
        responder = mock(Responder.class);

        getACLRequest = new GetACLRequest(TAG, ImapCommand.anyStateCommand("Name"), MAILBOX_NAME);

        imapSession.authenticated();
        imapSession.setMailboxSession(mailboxSession);
        when(messageManager.getMetaData(anyBoolean(), any(MailboxSession.class), any(FetchGroup.class)))
            .thenReturn(metaData);
        when(mailboxManager.getMailbox(any(MailboxPath.class), any(MailboxSession.class)))
            .thenReturn(messageManager);

        argumentCaptor = ArgumentCaptor.forClass(ImapResponseMessage.class);
    }

    @Test
    public void testNoListRight() throws Exception {
        when(mailboxManager.hasRight(path, MailboxACL.Right.Lookup, mailboxSession))
            .thenReturn(false);

        subject.doProcess(getACLRequest, responder, imapSession);

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

        subject.doProcess(getACLRequest, responder, imapSession);

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

        subject.doProcess(getACLRequest, responder, imapSession);

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }

    @Test
    public void testSufficientRights() throws Exception {
        MailboxACL acl = MailboxACL.OWNER_FULL_ACL;
        when(mailboxManager.hasRight(path, MailboxACL.Right.Lookup, mailboxSession))
            .thenReturn(true);
        when(mailboxManager.hasRight(path, MailboxACL.Right.Administer, mailboxSession))
            .thenReturn(true);
        when(metaData.getACL()).thenReturn(acl);

        ACLResponse response = new ACLResponse(MAILBOX_NAME, acl);
        subject.doProcess(getACLRequest, responder, imapSession);

        verify(responder, times(2)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(2);
        assertThat(argumentCaptor.getAllValues().get(0))
            .isEqualTo(response);
        assertThat(argumentCaptor.getAllValues().get(1))
            .matches(StatusResponseTypeMatcher.OK_RESPONSE_MATCHER::matches);
    }

}
