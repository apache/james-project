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

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapProcessor.Responder;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.ListRightsRequest;
import org.apache.james.imap.message.response.ListRightsResponse;
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
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;

/**
 * ListRightsProcessor Test.
 * 
 * @author Peter Palaga
 */
public class ListRightsProcessorTest {

    private static final String MAILBOX_NAME = ImapConstants.INBOX_NAME;
    private static final String USER_1 = "user1";

    ImapSession imapSessionStub;
    MailboxManager mailboxManagerStub;
    MailboxSession mailboxSessionStub;
    MessageManager messageManagerStub;
    MetaData metaDataStub;
    Mockery mockery = new JUnit4Mockery();
    ListRightsRequest listRightsRequest;
    UnpooledStatusResponseFactory statusResponseFactory;
    ListRightsProcessor subject;
    User user1Stub;
    EntryKey user1Key;
    Rfc4314Rights[] listRights;
    MailboxPath path;

    private Expectations prepareRightsExpectations() throws MailboxException {
        return new Expectations() {
            {

                allowing(imapSessionStub).getAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY);
                will(returnValue(mailboxSessionStub));

                allowing(imapSessionStub).getState();
                will(returnValue(ImapSessionState.AUTHENTICATED));

                allowing(mailboxSessionStub).getUser();
                will(returnValue(user1Stub));

                allowing(user1Stub).getUserName();
                will(returnValue(USER_1));

                allowing(mailboxManagerStub).startProcessingRequest(with(same(mailboxSessionStub)));
                allowing(mailboxManagerStub).endProcessingRequest(with(same(mailboxSessionStub)));

                allowing(messageManagerStub).getMetaData(with(any(Boolean.class)), with(same(mailboxSessionStub)), with(any(FetchGroup.class)));
                will(returnValue(metaDataStub));

            }
        };
    }

    @Before
    public void setUp() throws Exception {
        path = new MailboxPath("#private", USER_1, MAILBOX_NAME);
        statusResponseFactory = new UnpooledStatusResponseFactory();
        mailboxManagerStub = mockery.mock(MailboxManager.class);
        subject = new ListRightsProcessor(mockery.mock(ImapProcessor.class), mailboxManagerStub, statusResponseFactory, new NoopMetricFactory());
        imapSessionStub = mockery.mock(ImapSession.class);
        mailboxSessionStub = mockery.mock(MailboxSession.class);
        user1Stub = mockery.mock(User.class);
        messageManagerStub = mockery.mock(MessageManager.class);
        metaDataStub = mockery.mock(MetaData.class);

        listRightsRequest = new ListRightsRequest("TAG", ImapCommand.anyStateCommand("Name"), MAILBOX_NAME, USER_1);

        user1Key = EntryKey.deserialize(USER_1);
        listRights = new Rfc4314Rights[] {new Rfc4314Rights("ae"), new Rfc4314Rights("i"), new Rfc4314Rights("k")};
    }
    
    @Test
    public void testNoListRight() throws Exception {

        Expectations expectations = prepareRightsExpectations();
        expectations.allowing(mailboxManagerStub).hasRight(expectations.with(path), expectations.with(Expectations.equal(MailboxACL.Right.Lookup)), expectations.with(Expectations.same(mailboxSessionStub)));
        expectations.will(Expectations.returnValue(false));

        expectations.allowing(mailboxManagerStub).getMailbox(expectations.with(Expectations.any(MailboxPath.class)), expectations.with(Expectations.any(MailboxSession.class)));
        expectations.will(Expectations.returnValue(messageManagerStub));

        mockery.checking(expectations);

        final Responder responderMock = mockery.mock(Responder.class);
        mockery.checking(new Expectations() {
            {
                oneOf(responderMock).respond(with(new StatusResponseTypeMatcher(StatusResponse.Type.NO)));
            }
        });

        subject.doProcess(listRightsRequest, responderMock, imapSessionStub);

    }
    
    @Test
    public void testNoAdminRight() throws Exception {

        Expectations expectations = prepareRightsExpectations();
        expectations.allowing(mailboxManagerStub).hasRight(expectations.with(path), expectations.with(Expectations.equal(MailboxACL.Right.Lookup)), expectations.with(Expectations.same(mailboxSessionStub)));
        expectations.will(Expectations.returnValue(true));

        expectations.allowing(mailboxManagerStub).hasRight(expectations.with(path), expectations.with(Expectations.equal(MailboxACL.Right.Administer)), expectations.with(Expectations.same(mailboxSessionStub)));
        expectations.will(Expectations.returnValue(false));

        expectations.allowing(mailboxManagerStub).getMailbox(expectations.with(Expectations.any(MailboxPath.class)), expectations.with(Expectations.any(MailboxSession.class)));
        expectations.will(Expectations.returnValue(messageManagerStub));

        mockery.checking(expectations);

        final Responder responderMock = mockery.mock(Responder.class);
        mockery.checking(new Expectations() {
            {
                oneOf(responderMock).respond(with(new StatusResponseTypeMatcher(StatusResponse.Type.NO)));
            }
        });

        subject.doProcess(listRightsRequest, responderMock, imapSessionStub);

    }
    
    @Test
    public void testInexistentMailboxName() throws Exception {
        Expectations expectations = prepareRightsExpectations();
        
        expectations.allowing(mailboxManagerStub).getMailbox(expectations.with(Expectations.any(MailboxPath.class)), expectations.with(Expectations.any(MailboxSession.class)));
        expectations.will(Expectations.throwException(new MailboxNotFoundException(MAILBOX_NAME)));

        mockery.checking(expectations);

        final Responder responderMock = mockery.mock(Responder.class);
        mockery.checking(new Expectations() {
            {
                oneOf(responderMock).respond(with(new StatusResponseTypeMatcher(StatusResponse.Type.NO)));
            }
        });

        subject.doProcess(listRightsRequest, responderMock, imapSessionStub);
    }

    
    @Test
    public void testListRights() throws MailboxException {
        final MailboxACL acl = MailboxACL.OWNER_FULL_ACL;

        Expectations expectations = prepareRightsExpectations();
        
        expectations.allowing(mailboxManagerStub).getMailbox(expectations.with(Expectations.any(MailboxPath.class)), expectations.with(Expectations.any(MailboxSession.class)));
        expectations.will(Expectations.returnValue(messageManagerStub));
        
        expectations.allowing(mailboxManagerStub).hasRight(expectations.with(path), expectations.with(Expectations.equal(MailboxACL.Right.Lookup)), expectations.with(Expectations.same(mailboxSessionStub)));
        expectations.will(Expectations.returnValue(true));
        
        expectations.allowing(mailboxManagerStub).hasRight(expectations.with(path), expectations.with(Expectations.equal(MailboxACL.Right.Administer)), expectations.with(Expectations.same(mailboxSessionStub)));
        expectations.will(Expectations.returnValue(true));
        
        expectations.allowing(mailboxManagerStub).listRigths(expectations.with(path), expectations.with(Expectations.equal(user1Key)), expectations.with(Expectations.same(mailboxSessionStub)));
        expectations.will(Expectations.returnValue(listRights));

        expectations.allowing(metaDataStub).getACL();
        expectations.will(Expectations.returnValue(acl));

        mockery.checking(expectations);

        final ListRightsResponse response = new ListRightsResponse(MAILBOX_NAME, USER_1, listRights);
        final Responder responderMock = mockery.mock(Responder.class);
        mockery.checking(new Expectations() {
            {
                oneOf(responderMock).respond(with(equal(response)));
                oneOf(responderMock).respond(with(new StatusResponseTypeMatcher(StatusResponse.Type.OK)));
            }
        });

        subject.doProcess(listRightsRequest, responderMock, imapSessionStub);
    }

}
