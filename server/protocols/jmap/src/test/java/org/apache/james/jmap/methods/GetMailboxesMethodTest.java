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
package org.apache.james.jmap.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.mail.Flags;

import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.GetMailboxesRequest;
import org.apache.james.jmap.model.GetMailboxesResponse;
import org.apache.james.jmap.model.MailboxFactory;
import org.apache.james.jmap.model.mailbox.Mailbox;
import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.jmap.model.mailbox.SortOrder;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class GetMailboxesMethodTest {

    private static final String USERNAME = "username@domain.tld";
    private static final String USERNAME2 = "username2@domain.tld";

    private MailboxManager mailboxManager;
    private GetMailboxesMethod getMailboxesMethod;
    private ClientId clientId;
    private MailboxFactory mailboxFactory;

    @Before
    public void setup() throws Exception {
        clientId = ClientId.of("#0");
        InMemoryIntegrationResources inMemoryIntegrationResources = new InMemoryIntegrationResources();
        GroupMembershipResolver groupMembershipResolver = inMemoryIntegrationResources.createGroupMembershipResolver();
        mailboxManager = inMemoryIntegrationResources.createMailboxManager(groupMembershipResolver);
        mailboxFactory = new MailboxFactory(mailboxManager);

        getMailboxesMethod = new GetMailboxesMethod(mailboxManager, mailboxFactory, new DefaultMetricFactory());
    }

    @Test
    public void getMailboxesShouldReturnEmptyListWhenNoMailboxes() throws Exception {
        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        
        List<JmapResponse> getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, clientId, mailboxSession).collect(Collectors.toList());
        
        assertThat(getMailboxesResponse)
                .hasSize(1)
                .extracting(JmapResponse::getResponse)
                .hasOnlyElementsOfType(GetMailboxesResponse.class)
                .extracting(GetMailboxesResponse.class::cast)
                .flatExtracting(GetMailboxesResponse::getList)
                .isEmpty();
    }
    
    @Test
    public void getMailboxesShouldNotFailWhenMailboxManagerErrors() throws Exception {
        MailboxManager mockedMailboxManager = mock(MailboxManager.class);
        when(mockedMailboxManager.list(any()))
            .thenReturn(ImmutableList.of(new MailboxPath("namespace", "user", "name")));
        when(mockedMailboxManager.getMailbox(any(MailboxPath.class), any()))
            .thenThrow(new MailboxException());
        GetMailboxesMethod testee = new GetMailboxesMethod(mockedMailboxManager, mailboxFactory, new DefaultMetricFactory());
        
        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();
        MailboxSession session = new MockMailboxSession(USERNAME);
        
        List<JmapResponse> getMailboxesResponse = testee.process(getMailboxesRequest, clientId, session).collect(Collectors.toList());
        
        assertThat(getMailboxesResponse)
                .hasSize(1)
                .extracting(JmapResponse::getResponse)
                .hasOnlyElementsOfType(GetMailboxesResponse.class)
                .extracting(GetMailboxesResponse.class::cast)
                .flatExtracting(GetMailboxesResponse::getList)
                .isEmpty();
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWhenAvailable() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, "name");
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        List<JmapResponse> getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, clientId, mailboxSession).collect(Collectors.toList());
        
        assertThat(getMailboxesResponse)
                .hasSize(1)
                .extracting(JmapResponse::getResponse)
                .hasOnlyElementsOfType(GetMailboxesResponse.class)
                .extracting(GetMailboxesResponse.class::cast)
                .flatExtracting(GetMailboxesResponse::getList)
                .extracting(Mailbox::getId, Mailbox::getName, Mailbox::getUnreadMessages)
                .containsOnly(Tuple.tuple(InMemoryId.of(1), mailboxPath.getName(), 2L));
    }

    @Test
    public void getMailboxesShouldReturnOnlyMailboxesOfCurrentUserWhenAvailable() throws Exception {
        MailboxPath mailboxPathToReturn = MailboxPath.forUser(USERNAME, "mailboxToReturn");
        MailboxPath mailboxPathtoSkip = MailboxPath.forUser(USERNAME2, "mailboxToSkip");
        MailboxSession userSession = mailboxManager.createSystemSession(USERNAME);
        MailboxSession user2Session = mailboxManager.createSystemSession(USERNAME2);
        mailboxManager.createMailbox(mailboxPathToReturn, userSession);
        mailboxManager.createMailbox(mailboxPathtoSkip, user2Session);

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        List<JmapResponse> getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, clientId, userSession).collect(Collectors.toList());
        
        assertThat(getMailboxesResponse)
                .hasSize(1)
                .extracting(JmapResponse::getResponse)
                .hasOnlyElementsOfType(GetMailboxesResponse.class)
                .extracting(GetMailboxesResponse.class::cast)
                .flatExtracting(GetMailboxesResponse::getList)
                .extracting(Mailbox::getId, Mailbox::getName)
                .containsOnly(Tuple.tuple(InMemoryId.of(1), mailboxPathToReturn.getName()));
    }

    @Test
    public void getMailboxesShouldReturnInboxWithSortOrder10() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, "INBOX");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        List<JmapResponse> getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, clientId, mailboxSession).collect(Collectors.toList());

        assertThat(getMailboxesResponse)
                .hasSize(1)
                .extracting(JmapResponse::getResponse)
                .hasOnlyElementsOfType(GetMailboxesResponse.class)
                .extracting(GetMailboxesResponse.class::cast)
                .flatExtracting(GetMailboxesResponse::getList)
                .extracting(Mailbox::getSortOrder)
                .containsOnly(SortOrder.of(10));
    }

    @Test
    public void getMailboxesShouldReturnSortOrder1000OnUnknownFolder() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, "unknown");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        List<JmapResponse> getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, clientId, mailboxSession).collect(Collectors.toList());

        assertThat(getMailboxesResponse)
                .hasSize(1)
                .extracting(JmapResponse::getResponse)
                .hasOnlyElementsOfType(GetMailboxesResponse.class)
                .extracting(GetMailboxesResponse.class::cast)
                .flatExtracting(GetMailboxesResponse::getList)
                .extracting(Mailbox::getSortOrder)
                .containsOnly(SortOrder.of(1000));
    }

    @Test
    public void getMailboxesShouldReturnInboxWithSortOrder10OnDifferenceCase() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, "InBoX");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        List<JmapResponse> getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, clientId, mailboxSession).collect(Collectors.toList());

        assertThat(getMailboxesResponse)
                .hasSize(1)
                .extracting(JmapResponse::getResponse)
                .hasOnlyElementsOfType(GetMailboxesResponse.class)
                .extracting(GetMailboxesResponse.class::cast)
                .flatExtracting(GetMailboxesResponse::getList)
                .extracting(Mailbox::getSortOrder)
                .containsOnly(SortOrder.of(10));
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWithSortOrder() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "INBOX"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "ARCHIVE"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "DRAFTS"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "OUTBOX"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "SENT"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "TRASH"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "SPAM"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "TEMPLATES"), mailboxSession);

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        List<JmapResponse> getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, clientId, mailboxSession).collect(Collectors.toList());

        assertThat(getMailboxesResponse)
                .hasSize(1)
                .extracting(JmapResponse::getResponse)
                .hasOnlyElementsOfType(GetMailboxesResponse.class)
                .extracting(GetMailboxesResponse.class::cast)
                .flatExtracting(GetMailboxesResponse::getList)
                .extracting(Mailbox::getName, Mailbox::getSortOrder)
                .containsExactly(
                        Tuple.tuple("INBOX", SortOrder.of(10)),
                        Tuple.tuple("ARCHIVE", SortOrder.of(20)),
                        Tuple.tuple("DRAFTS", SortOrder.of(30)),
                        Tuple.tuple("OUTBOX", SortOrder.of(40)),
                        Tuple.tuple("SENT", SortOrder.of(50)),
                        Tuple.tuple("TRASH", SortOrder.of(60)),
                        Tuple.tuple("SPAM", SortOrder.of(70)),
                        Tuple.tuple("TEMPLATES", SortOrder.of(80)));
    }

    @Test
    public void getMailboxesShouldReturnEmptyMailboxByDefault() throws MailboxException {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, "name");
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        List<JmapResponse> getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, clientId, mailboxSession).collect(Collectors.toList());

        assertThat(getMailboxesResponse)
                .hasSize(1)
                .extracting(JmapResponse::getResponse)
                .hasOnlyElementsOfType(GetMailboxesResponse.class)
                .extracting(GetMailboxesResponse.class::cast)
                .flatExtracting(GetMailboxesResponse::getList)
                .extracting(Mailbox::getTotalMessages, Mailbox::getUnreadMessages)
                .containsOnly(Tuple.tuple(0L, 0L));
    }

    @Test
    public void getMailboxesShouldReturnCorrectTotalMessagesCount() throws MailboxException {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, "name");
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        List<JmapResponse> getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, clientId, mailboxSession).collect(Collectors.toList());

        assertThat(getMailboxesResponse)
                .hasSize(1)
                .extracting(JmapResponse::getResponse)
                .hasOnlyElementsOfType(GetMailboxesResponse.class)
                .extracting(GetMailboxesResponse.class::cast)
                .flatExtracting(GetMailboxesResponse::getList)
                .extracting(Mailbox::getTotalMessages)
                .containsExactly(2L);
    }

    @Test
    public void getMailboxesShouldReturnCorrectUnreadMessagesCount() throws MailboxException {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, "name");
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
        Flags defaultUnseenFlag = new Flags();
        Flags readMessageFlag = new Flags();
        readMessageFlag.add(Flags.Flag.SEEN);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, defaultUnseenFlag );
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, defaultUnseenFlag );
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test3\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, readMessageFlag);
        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        List<JmapResponse> getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, clientId, mailboxSession).collect(Collectors.toList());

        assertThat(getMailboxesResponse)
                .hasSize(1)
                .extracting(JmapResponse::getResponse)
                .hasOnlyElementsOfType(GetMailboxesResponse.class)
                .extracting(GetMailboxesResponse.class::cast)
                .flatExtracting(GetMailboxesResponse::getList)
                .extracting(Mailbox::getUnreadMessages)
                .containsExactly(2L);
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWithRoles() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "INBOX"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "ARCHIVE"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "DRAFTS"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "OUTBOX"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "SENT"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "TRASH"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "SPAM"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "TEMPLATES"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "WITHOUT ROLE"), mailboxSession);
        
        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        List<JmapResponse> getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, clientId, mailboxSession).collect(Collectors.toList());

        assertThat(getMailboxesResponse)
                .hasSize(1)
                .extracting(JmapResponse::getResponse)
                .hasOnlyElementsOfType(GetMailboxesResponse.class)
                .extracting(GetMailboxesResponse.class::cast)
                .flatExtracting(GetMailboxesResponse::getList)
                .extracting(Mailbox::getName, Mailbox::getRole)
                .containsOnly(
                        Tuple.tuple("INBOX", Optional.of(Role.INBOX)),
                        Tuple.tuple("ARCHIVE", Optional.of(Role.ARCHIVE)),
                        Tuple.tuple("DRAFTS", Optional.of(Role.DRAFTS)),
                        Tuple.tuple("OUTBOX", Optional.of(Role.OUTBOX)),
                        Tuple.tuple("SENT", Optional.of(Role.SENT)),
                        Tuple.tuple("TRASH", Optional.of(Role.TRASH)),
                        Tuple.tuple("SPAM", Optional.of(Role.SPAM)),
                        Tuple.tuple("TEMPLATES", Optional.of(Role.TEMPLATES)),
                        Tuple.tuple("WITHOUT ROLE", Optional.empty()));
    }
}
