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

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapProcessor.Responder;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.ListRightsRequest;
import org.apache.james.imap.message.response.ListRightsResponse;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MailboxMetaData;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * ListRightsProcessor Test.
 */
class ListRightsProcessorTest {

    private static final String MAILBOX_NAME = ImapConstants.INBOX_NAME;
    private static final Username USER_1 = Username.of("user1");

    private FakeImapSession imapSession;
    private MailboxManager mailboxManager;
    private MailboxSession mailboxSession;
    private MailboxMetaData metaData;
    private ListRightsRequest listRightsRequest;
    private ListRightsProcessor subject;
    private EntryKey user1Key;
    private List<Rfc4314Rights> listRights;
    private MailboxPath path;
    private Responder responder;
    private ArgumentCaptor<ImapResponseMessage> argumentCaptor;

    @BeforeEach
    public void setUp() throws Exception {
        path = MailboxPath.forUser(USER_1, MAILBOX_NAME);
        UnpooledStatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        mailboxManager = mock(MailboxManager.class);
        subject = new ListRightsProcessor(mock(ImapProcessor.class), mailboxManager, statusResponseFactory, new RecordingMetricFactory());
        imapSession = new FakeImapSession();
        mailboxSession = MailboxSessionUtil.create(USER_1);
        MessageManager messageManager = mock(MessageManager.class);
        metaData = mock(MailboxMetaData.class);
        responder = mock(Responder.class);

        argumentCaptor = ArgumentCaptor.forClass(ImapResponseMessage.class);

        imapSession.authenticated();
        imapSession.setMailboxSession(mailboxSession);
        when(messageManager.getMetaData(anyBoolean(), any(MailboxSession.class), any(MailboxMetaData.FetchGroup.class)))
            .thenReturn(metaData);
        when(mailboxManager.getMailbox(any(MailboxPath.class), any(MailboxSession.class)))
            .thenReturn(messageManager);

        listRightsRequest = new ListRightsRequest(TAG, MAILBOX_NAME, USER_1.asString());

        user1Key = EntryKey.deserialize(USER_1.asString());
        listRights = List.of(
            Rfc4314Rights.fromSerializedRfc4314Rights("ae"),
            Rfc4314Rights.fromSerializedRfc4314Rights("i"),
            Rfc4314Rights.fromSerializedRfc4314Rights("k"));
    }
    
    @Test
    void testNoListRight() throws Exception {
        when(mailboxManager.hasRight(path, MailboxACL.Right.Lookup, mailboxSession))
            .thenReturn(false);

        subject.doProcess(listRightsRequest, responder, imapSession);

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }
    
    @Test
    void testNoAdminRight() throws Exception {
        when(mailboxManager.hasRight(path, MailboxACL.Right.Lookup, mailboxSession))
            .thenReturn(true);
        when(mailboxManager.hasRight(path, MailboxACL.Right.Administer, mailboxSession))
            .thenReturn(false);

        subject.doProcess(listRightsRequest, responder, imapSession);

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }
    
    @Test
    void testInexistentMailboxName() throws Exception {
        when(mailboxManager.getMailbox(any(MailboxPath.class), any(MailboxSession.class)))
            .thenThrow(new MailboxNotFoundException(""));

        subject.doProcess(listRightsRequest, responder, imapSession);

        verify(responder, times(1)).respond(argumentCaptor.capture());
        verifyNoMoreInteractions(responder);

        assertThat(argumentCaptor.getAllValues())
            .hasSize(1);
        assertThat(argumentCaptor.getAllValues().get(0))
            .matches(StatusResponseTypeMatcher.NO_RESPONSE_MATCHER::matches);
    }

    
    @Test
    void testListRights() throws MailboxException {
        MailboxACL acl = MailboxACL.OWNER_FULL_ACL;
        when(mailboxManager.hasRight(path, MailboxACL.Right.Lookup, mailboxSession))
            .thenReturn(true);
        when(mailboxManager.hasRight(path, MailboxACL.Right.Administer, mailboxSession))
            .thenReturn(true);
        when(metaData.getACL()).thenReturn(acl);
        
        when(mailboxManager.listRights(path, user1Key, mailboxSession))
            .thenReturn(listRights);


        subject.doProcess(listRightsRequest, responder, imapSession);

        ListRightsResponse response = new ListRightsResponse(MAILBOX_NAME, USER_1.asString(), listRights);
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
