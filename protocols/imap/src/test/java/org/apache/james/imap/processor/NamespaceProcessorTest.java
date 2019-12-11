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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapProcessor.Responder;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.NamespaceRequest;
import org.apache.james.imap.message.response.NamespaceResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.Before;
import org.junit.Test;

public class NamespaceProcessorTest {

    private static final String SHARED_PREFIX = "SharedPrefix";
    private static final String USERS_PREFIX = "UsersPrefix";
    private static final String PERSONAL_PREFIX = "PersonalPrefix";
    
    
    NamespaceProcessor subject;
    StatusResponseFactory statusResponseStub;
    ImapSession imapSession;
    MailboxSession mailboxSession;
    NamespaceRequest namespaceRequest;
    Collection<String> sharedSpaces;
    MailboxManager mailboxManagerStub;

    @Before
    public void setUp() throws Exception {
        sharedSpaces = new ArrayList<>();
        statusResponseStub = mock(StatusResponseFactory.class);
        mailboxManagerStub = mock(MailboxManager.class);
        subject = new NamespaceProcessor(mock(ImapProcessor.class), mailboxManagerStub, statusResponseStub, new NoopMetricFactory());
        imapSession = spy(new FakeImapSession());
        mailboxSession = mock(MailboxSession.class);
     
        namespaceRequest = new NamespaceRequest(ImapCommand.anyStateCommand("Name"), TAG);
        imapSession.setMailboxSession(mailboxSession);
    }

    @Test
    public void testNamespaceResponseShouldContainPersonalAndUserSpaces() {
        when(imapSession.supportMultipleNamespaces()).thenReturn(true);

        when(mailboxSession.getPersonalSpace()).thenReturn(PERSONAL_PREFIX);
        when(mailboxSession.getOtherUsersSpace()).thenReturn(USERS_PREFIX);
        when(mailboxSession.getSharedSpaces()).thenReturn(new ArrayList<>());
        when(mailboxSession.getPathDelimiter()).thenReturn(MailboxConstants.DEFAULT_DELIMITER);

        when(imapSession.getState()).thenReturn(ImapSessionState.AUTHENTICATED);
        when(statusResponseStub.taggedOk(any(Tag.class), any(ImapCommand.class), any(HumanReadableText.class)))
            .thenReturn(mock(StatusResponse.class));

        final NamespaceResponse response = buildResponse(null);
        final Responder responderMock = mock(Responder.class);

        subject.doProcess(namespaceRequest, responderMock, imapSession);

        verify(responderMock, times(1)).respond(response);
        verify(responderMock, times(1)).respond(any(StatusResponse.class));
        verifyNoMoreInteractions(responderMock);
    }
    
    @Test
    public void testNamespaceResponseShouldContainSharedSpaces() {
        when(imapSession.supportMultipleNamespaces()).thenReturn(true);

        when(mailboxSession.getPersonalSpace()).thenReturn(PERSONAL_PREFIX);
        when(mailboxSession.getOtherUsersSpace()).thenReturn(USERS_PREFIX);
        when(mailboxSession.getSharedSpaces()).thenReturn(Arrays.asList(SHARED_PREFIX));
        when(mailboxSession.getPathDelimiter()).thenReturn(MailboxConstants.DEFAULT_DELIMITER);

        when(imapSession.getState()).thenReturn(ImapSessionState.AUTHENTICATED);
        when(statusResponseStub.taggedOk(any(Tag.class), any(ImapCommand.class), any(HumanReadableText.class)))
            .thenReturn(mock(StatusResponse.class));
        
        final List<NamespaceResponse.Namespace> sharedSpaces = new ArrayList<>();
        sharedSpaces.add(new NamespaceResponse.Namespace(SHARED_PREFIX, MailboxConstants.DEFAULT_DELIMITER));
        final NamespaceResponse response = buildResponse(sharedSpaces);
        
        final Responder responderMock = mock(Responder.class);

        subject.doProcess(namespaceRequest, responderMock, imapSession);

        verify(responderMock, times(1)).respond(response);
        verify(responderMock, times(1)).respond(any(StatusResponse.class));
        verifyNoMoreInteractions(responderMock);
    }

    private NamespaceResponse buildResponse(List<NamespaceResponse.Namespace> sharedSpaces) {
       
        final List<NamespaceResponse.Namespace> personalSpaces = new ArrayList<>();
        personalSpaces.add(new NamespaceResponse.Namespace(PERSONAL_PREFIX, MailboxConstants.DEFAULT_DELIMITER));
        final List<NamespaceResponse.Namespace> otherUsersSpaces = new ArrayList<>();
        otherUsersSpaces.add(new NamespaceResponse.Namespace(USERS_PREFIX, MailboxConstants.DEFAULT_DELIMITER));

        return new NamespaceResponse(personalSpaces, otherUsersSpaces, sharedSpaces);
    }
}

