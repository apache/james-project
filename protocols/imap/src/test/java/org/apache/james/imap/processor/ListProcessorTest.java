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
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.imap.message.response.ListResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class ListProcessorTest  {

    ListProcessor processor;

    ImapProcessor next;

    MailboxManager manager;

    ImapProcessor.Responder responder;

    MailboxMetaData result;

    ImapSession session;

    ImapCommand command;

    StatusResponseFactory serverResponseFactory;

    MailboxPath inboxPath = new MailboxPath("", "", "INBOX");

    private Mockery mockery = new JUnit4Mockery();
    
    @Before
    public void setUp() throws Exception {
        serverResponseFactory = mockery.mock(StatusResponseFactory.class);
        session = mockery.mock(ImapSession.class);
        command = ImapCommand.anyStateCommand("Command");
        next = mockery.mock(ImapProcessor.class);
        responder = mockery.mock(ImapProcessor.Responder.class);
        result = mockery.mock(MailboxMetaData.class);
        manager = mockery.mock(MailboxManager.class);
        processor = createProcessor(next, manager, serverResponseFactory);
    }

    ListProcessor createProcessor(ImapProcessor next,
            MailboxManager manager, StatusResponseFactory factory) {
        return new ListProcessor(next, manager, factory);
    }

    ListResponse createResponse(boolean noinferior, boolean noselect,
            boolean marked, boolean unmarked, boolean hasChildren,
            boolean hasNoChildren, char hierarchyDelimiter, String mailboxName) {
        return new ListResponse(noinferior, noselect, marked, unmarked,
                hasChildren, hasNoChildren, MailboxConstants.USER_NAMESPACE + MailboxConstants.DEFAULT_DELIMITER + mailboxName, hierarchyDelimiter);
    }

    void setUpResult(final MailboxMetaData.Children children, final MailboxMetaData.Selectability selectability,
            final char hierarchyDelimiter, final MailboxPath path) {
        mockery.checking(new Expectations() {{
            oneOf(result).inferiors();will(returnValue(children));
            oneOf(result).getSelectability();will(returnValue(selectability));
            oneOf(result).getHierarchyDelimiter();will(returnValue(hierarchyDelimiter));
            oneOf(result).getPath();will(returnValue(path));
        }});
    }
    
    @Test
    public void testHasChildren() throws Exception {
        setUpResult(MailboxMetaData.Children.HAS_CHILDREN, MailboxMetaData.Selectability.NONE, '.', inboxPath);
        mockery.checking(new Expectations() {{
            oneOf(responder).respond(with(equal(createResponse(false, false, false, false, true, false, '.', "INBOX"))));
        }});
        processor.processResult(responder, false, result,MailboxType.OTHER);
    }

    @Test
    public void testHasNoChildren() throws Exception {
        setUpResult(MailboxMetaData.Children.HAS_NO_CHILDREN, MailboxMetaData.Selectability.NONE, '.', inboxPath);
        mockery.checking(new Expectations() {{
            oneOf(responder).respond(with(equal(createResponse(false, false, false, false, false, true, '.', "INBOX"))));
        }});
        processor.processResult(responder, false, result,MailboxType.OTHER);
    }
    
    @Test
    public void testNoInferiors() throws Exception {
        setUpResult(MailboxMetaData.Children.NO_INFERIORS, MailboxMetaData.Selectability.NONE, '.', inboxPath);
        mockery.checking(new Expectations() {{
            oneOf(responder).respond(with(equal(createResponse(true, false, false, false, false, false, '.', "INBOX"))));
        }});
        processor.processResult(responder, false, result,MailboxType.OTHER);
    }

    @Test
    public void testNoSelect() throws Exception {
        setUpResult(MailboxMetaData.Children.CHILDREN_ALLOWED_BUT_UNKNOWN, MailboxMetaData.Selectability.NOSELECT, '.', inboxPath);
        mockery.checking(new Expectations() {{
            oneOf(responder).respond(with(equal(createResponse(false, true, false, false, false, false, '.', "INBOX"))));
        }});
        processor.processResult(responder, false, result,MailboxType.OTHER);
    }

    @Test
    public void testUnMarked() throws Exception {
        setUpResult(MailboxMetaData.Children.CHILDREN_ALLOWED_BUT_UNKNOWN, MailboxMetaData.Selectability.UNMARKED, '.', inboxPath);
        mockery.checking(new Expectations() {{
            oneOf(responder).respond(with(equal(createResponse(false, false, false, true, false, false, '.', "INBOX"))));
        }});
        processor.processResult(responder, false, result,MailboxType.OTHER);
    }

    @Test
    public void testMarked() throws Exception {
        setUpResult(MailboxMetaData.Children.CHILDREN_ALLOWED_BUT_UNKNOWN, MailboxMetaData.Selectability.MARKED, '.', inboxPath);
        mockery.checking(new Expectations() {{
            oneOf(responder).respond(with(equal(createResponse(false, false, true, false, false, false, '.', "INBOX"))));
        }});
        processor.processResult(responder, false, result,MailboxType.OTHER);
    }
}
