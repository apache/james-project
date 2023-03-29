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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.ImapProcessor.Responder;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.DeleteACLRequest;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MailboxMetaData;
import org.apache.james.mailbox.MessageManager.MailboxMetaData.FetchGroup;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import reactor.core.publisher.Mono;

/**
 * DeleteACLProcessor Test.
 */
class DeleteACLProcessorTest {
    private static final String MAILBOX_NAME = ImapConstants.INBOX_NAME;
    private static final Username USER_1 = Username.of("user1");

    private FakeImapSession imapSession;
    private MailboxManager mailboxManager;
    private MailboxSession mailboxSession;
    private MailboxMetaData metaData;
    private DeleteACLRequest deleteACLRequest;
    private DeleteACLProcessor subject;
    private EntryKey user1Key;
    private MailboxPath path;
    private Responder responder;
    private ArgumentCaptor<ImapResponseMessage> argumentCaptor;

    @BeforeEach
    void setUp() throws Exception {
        path = MailboxPath.forUser(USER_1, MAILBOX_NAME);
        UnpooledStatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.manageProcessing(any(), any())).thenAnswer((Answer<Mono>) invocation -> {
            Object[] args = invocation.getArguments();
            return (Mono) args[0];
        });
        subject = new DeleteACLProcessor(mailboxManager, statusResponseFactory, new RecordingMetricFactory());
        imapSession = new FakeImapSession();
        mailboxSession = MailboxSessionUtil.create(USER_1);

        MessageManager messageManager = mock(MessageManager.class);
        metaData = mock(MailboxMetaData.class);
        responder = mock(Responder.class);

        imapSession.authenticated();
        imapSession.setMailboxSession(mailboxSession);
        when(messageManager.getMetaData(any(MailboxMetaData.RecentMode.class), any(MailboxSession.class), any(FetchGroup.class)))
            .thenReturn(metaData);
        when(mailboxManager.getMailbox(any(MailboxPath.class), any(MailboxSession.class)))
            .thenReturn(messageManager);

        deleteACLRequest = new DeleteACLRequest(TAG,
            MAILBOX_NAME,
            USER_1.asString());

        user1Key = EntryKey.deserialize(USER_1.asString());

        argumentCaptor = ArgumentCaptor.forClass(ImapResponseMessage.class);
    }
    
    @Test
    void testNoListRight() {
        when(mailboxManager.hasRightReactive(path, MailboxACL.Right.Lookup, mailboxSession))
            .thenReturn(Mono.just(false));

        subject.doProcess(deleteACLRequest, responder, imapSession).block();

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }

    @Test
    void testNoAdminRight() {
        when(mailboxManager.hasRightReactive(path, MailboxACL.Right.Lookup, mailboxSession))
            .thenReturn(Mono.just(true));
        when(mailboxManager.hasRightReactive(path, MailboxACL.Right.Administer, mailboxSession))
            .thenReturn(Mono.just(false));

        subject.doProcess(deleteACLRequest, responder, imapSession).block();

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }

    @Test
    void testNonExistentMailboxName() {
        when(mailboxManager.hasRightReactive(any(MailboxPath.class),
            any(),any(MailboxSession.class)))
            .thenReturn(Mono.error(new MailboxNotFoundException("")));

        subject.doProcess(deleteACLRequest, responder, imapSession).block();

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }

    
    @Test
    void testDelete() {
        MailboxACL acl = MailboxACL.OWNER_FULL_ACL;
        when(mailboxManager.hasRightReactive(path, MailboxACL.Right.Lookup, mailboxSession))
            .thenReturn(Mono.just(true));
        when(mailboxManager.hasRightReactive(path, MailboxACL.Right.Administer, mailboxSession))
            .thenReturn(Mono.just(true));
        when(metaData.getACL()).thenReturn(acl);
        when(mailboxManager.applyRightsCommandReactive(path, MailboxACL.command().key(user1Key).noRights().asReplacement(),
            mailboxSession))
            .thenReturn(Mono.empty());

        subject.doProcess(deleteACLRequest, responder, imapSession).block();

        verify(mailboxManager).applyRightsCommandReactive(path,
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
