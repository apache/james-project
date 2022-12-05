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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.EnumSet;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.ImapProcessor.Responder;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.GetACLRequest;
import org.apache.james.imap.message.response.ACLResponse;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MailboxMetaData;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import reactor.core.publisher.Mono;

/**
 * GetACLProcessor Test.
 * 
 */
class GetACLProcessorTest {

    private static final String MAILBOX_NAME = ImapConstants.INBOX_NAME;
    private static final Username USER_1 = Username.of("user1");

    private FakeImapSession imapSession;
    private MailboxManager mailboxManager;
    private MailboxSession mailboxSession;
    private MailboxMetaData metaData;
    private GetACLRequest getACLRequest;
    private GetACLProcessor subject;
    private ArgumentCaptor<ImapResponseMessage> argumentCaptor;
    private Responder responder;

    @BeforeEach
    public void setUp() throws Exception {
        UnpooledStatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.manageProcessing(any(), any())).thenAnswer((Answer<Mono>) invocation -> {
            Object[] args = invocation.getArguments();
            return (Mono) args[0];
        });
        subject = new GetACLProcessor(mailboxManager, statusResponseFactory, new RecordingMetricFactory());
        imapSession = new FakeImapSession();
        mailboxSession = MailboxSessionUtil.create(USER_1);
        MessageManager messageManager = mock(MessageManager.class);
        metaData = mock(MailboxMetaData.class);
        responder = mock(Responder.class);

        getACLRequest = new GetACLRequest(TAG, MAILBOX_NAME);

        imapSession.authenticated();
        imapSession.setMailboxSession(mailboxSession);
        when(messageManager.getMetaDataReactive(any(MailboxMetaData.RecentMode.class), any(MailboxSession.class), any(EnumSet.class)))
            .thenReturn(Mono.just(metaData));
        when(mailboxManager.getMailboxReactive(any(MailboxPath.class), any(MailboxSession.class)))
            .thenReturn(Mono.just(messageManager));
        when(messageManager.getMailboxEntity()).thenReturn(mock(Mailbox.class));

        argumentCaptor = ArgumentCaptor.forClass(ImapResponseMessage.class);
    }

    @Test
    void testNoListRight() throws Exception {
        when(mailboxManager.hasRight(any(Mailbox.class), eq(MailboxACL.Right.Lookup), eq(mailboxSession)))
            .thenReturn(false);

        subject.doProcess(getACLRequest, responder, imapSession).block();

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }
    
    @Test
    void testNoAdminRight() throws Exception {
        when(mailboxManager.hasRight(any(Mailbox.class), eq(MailboxACL.Right.Lookup), eq(mailboxSession)))
            .thenReturn(true);
        when(mailboxManager.hasRight(any(Mailbox.class), eq(MailboxACL.Right.Administer), eq(mailboxSession)))
            .thenReturn(false);

        subject.doProcess(getACLRequest, responder, imapSession).block();

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }
    
    @Test
    void testInexistentMailboxName() {
        when(mailboxManager.getMailboxReactive(any(MailboxPath.class), any(MailboxSession.class)))
            .thenReturn(Mono.error(new MailboxNotFoundException("")));

        subject.doProcess(getACLRequest, responder, imapSession).block();

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }

    @Test
    void testSufficientRights() throws Exception {
        MailboxACL acl = MailboxACL.OWNER_FULL_ACL;
        when(mailboxManager.hasRight(any(Mailbox.class), eq(MailboxACL.Right.Lookup), eq(mailboxSession)))
            .thenReturn(true);
        when(mailboxManager.hasRight(any(Mailbox.class), eq(MailboxACL.Right.Administer), eq(mailboxSession)))
            .thenReturn(true);
        when(metaData.getACL()).thenReturn(acl);

        subject.doProcess(getACLRequest, responder, imapSession).block();

        verify(responder, times(2)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(2);
        ACLResponse response = new ACLResponse(MAILBOX_NAME, acl);
        assertThat(argumentCaptor.getAllValues().get(0))
            .isEqualTo(response);
        assertThat(argumentCaptor.getAllValues().get(1))
            .matches(StatusResponseTypeMatcher.OK_RESPONSE_MATCHER::matches);
    }

}
