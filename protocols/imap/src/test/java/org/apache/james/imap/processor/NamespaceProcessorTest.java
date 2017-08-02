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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapProcessor.Responder;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.NamespaceRequest;
import org.apache.james.imap.message.response.NamespaceResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class NamespaceProcessorTest {

    private static final String SHARED_PREFIX = "SharedPrefix";
    private static final String USERS_PREFIX = "UsersPrefix";
    private static final String PERSONAL_PREFIX = "PersonalPrefix";
    
    
    NamespaceProcessor subject;
    StatusResponseFactory statusResponseStub;
    ImapSession imapSessionStub;
    MailboxSession mailboxSessionStub;
    String personalSpaceStub;
    String usersSpaceStub;
    String sharedSpaceStub;
    NamespaceRequest namespaceRequest;
    Collection<String> sharedSpaces;
    MailboxManager mailboxManagerStub;
    Mockery mockery = new JUnit4Mockery();
    
    @Before
    public void setUp() throws Exception {
        sharedSpaces = new ArrayList<>();
        statusResponseStub = mockery.mock(StatusResponseFactory.class);
        mailboxManagerStub = mockery.mock(MailboxManager.class);
        subject = new NamespaceProcessor(mockery.mock(ImapProcessor.class), mailboxManagerStub, statusResponseStub, new NoopMetricFactory());
        imapSessionStub = mockery.mock(ImapSession.class);
        mailboxSessionStub = mockery.mock(MailboxSession.class);
     
        namespaceRequest = new NamespaceRequest(ImapCommand.anyStateCommand("Name"), "TAG");
       
    }
    

    
    @Test
    public void testNamespaceResponseShouldContainPersonalAndUserSpaces() throws Exception {
        mockery.checking (new Expectations() {{
            allowing(imapSessionStub).supportMultipleNamespaces(); will(returnValue(true));
            allowing(imapSessionStub).getAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY); will(returnValue(mailboxSessionStub));
            allowing(imapSessionStub).getAttribute(EnableProcessor.ENABLED_CAPABILITIES); will(returnValue(null));
            allowing(any(ImapSession.class)).method("setAttribute");

            allowing(mailboxSessionStub).getPersonalSpace(); will(returnValue(PERSONAL_PREFIX));
            allowing(mailboxSessionStub).getOtherUsersSpace(); will(returnValue(USERS_PREFIX));
            allowing(mailboxSessionStub).getSharedSpaces();will(returnValue(new ArrayList<String>()));
            allowing(mailboxSessionStub).getPathDelimiter();will(returnValue(MailboxConstants.DEFAULT_DELIMITER));
            allowing(imapSessionStub).getState();will(returnValue(ImapSessionState.AUTHENTICATED));
            allowing(statusResponseStub).taggedOk(
                    with(any(String.class)), with(any(ImapCommand.class)), 
                    with(any(HumanReadableText.class)), with(any(ResponseCode.class))); will(returnValue(mockery.mock(StatusResponse.class)));
            ignoring(imapSessionStub);
            ignoring(mailboxSessionStub);
            ignoring(mailboxManagerStub);
            ignoring(statusResponseStub);
        }});
        
        final NamespaceResponse response = buildResponse(null);
        
        final Responder responderMock = expectResponse(response);
        
        subject.doProcess(namespaceRequest, responderMock, imapSessionStub);
    }
    
    @Test
    public void testNamespaceResponseShouldContainSharedSpaces() throws Exception {
        mockery.checking (new Expectations() {{
            allowing(imapSessionStub).supportMultipleNamespaces(); will(returnValue(true));
            allowing(imapSessionStub).getAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY); will(returnValue(mailboxSessionStub));
            allowing(imapSessionStub).getAttribute(EnableProcessor.ENABLED_CAPABILITIES); will(returnValue(null));
            allowing(any(ImapSession.class)).method("setAttribute");
            allowing(mailboxSessionStub).getPersonalSpace(); will(returnValue(PERSONAL_PREFIX));
            allowing(mailboxSessionStub).getOtherUsersSpace(); will(returnValue(USERS_PREFIX));
            allowing(mailboxSessionStub).getSharedSpaces();will(returnValue(Arrays.asList(SHARED_PREFIX)));
            allowing(mailboxSessionStub).getPathDelimiter();will(returnValue(MailboxConstants.DEFAULT_DELIMITER));
            allowing(imapSessionStub).getState();will(returnValue(ImapSessionState.AUTHENTICATED));
            allowing(statusResponseStub).taggedOk(
                    with(any(String.class)), with(any(ImapCommand.class)), 
                    with(any(HumanReadableText.class)), with(any(ResponseCode.class))); will(returnValue(mockery.mock(StatusResponse.class)));
            ignoring(imapSessionStub);
            ignoring(mailboxSessionStub);
            ignoring(mailboxManagerStub);
            ignoring(statusResponseStub);
        }});
        this.sharedSpaces.add(sharedSpaceStub);
        
        
        final List<NamespaceResponse.Namespace> sharedSpaces = new ArrayList<>();
        sharedSpaces.add(new NamespaceResponse.Namespace(SHARED_PREFIX, MailboxConstants.DEFAULT_DELIMITER));
        final NamespaceResponse response = buildResponse(sharedSpaces);
        
        final Responder responderMock = expectResponse(response);
        
        subject.doProcess(namespaceRequest, responderMock, imapSessionStub);
    }

    private NamespaceResponse buildResponse(List<NamespaceResponse.Namespace> sharedSpaces) {
       
        final List<NamespaceResponse.Namespace> personalSpaces = new ArrayList<>();
        personalSpaces.add(new NamespaceResponse.Namespace(PERSONAL_PREFIX, MailboxConstants.DEFAULT_DELIMITER));
        final List<NamespaceResponse.Namespace> otherUsersSpaces = new ArrayList<>();
        otherUsersSpaces.add(new NamespaceResponse.Namespace(USERS_PREFIX, MailboxConstants.DEFAULT_DELIMITER));

        return new NamespaceResponse(personalSpaces, otherUsersSpaces, sharedSpaces);
    }

    private Responder expectResponse(final NamespaceResponse response) {
        final Responder responderMock = mockery.mock(Responder.class);
        mockery.checking(new Expectations(){{
            oneOf(responderMock).respond(with(equal(response)));
            oneOf(responderMock).respond(with(any(StatusResponse.class)));
        }});
        return responderMock;
    }
}

