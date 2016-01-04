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

import java.io.ByteArrayInputStream;
import java.util.Date;

import javax.mail.Flags;

import org.apache.james.jmap.model.GetMailboxesRequest;
import org.apache.james.jmap.model.GetMailboxesResponse;
import org.apache.james.jmap.model.mailbox.Mailbox;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MockAuthenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetMailboxesMethodTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetMailboxesMethodTest.class);
    private static final String USERNAME = "username@domain.tld";

    private StoreMailboxManager<InMemoryId> mailboxManager;
    private GetMailboxesMethod<InMemoryId> getMailboxesMethod;
    
    @Before
    public void setup() throws Exception {
        InMemoryMailboxSessionMapperFactory mailboxMapperFactory = new InMemoryMailboxSessionMapperFactory();
        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        mailboxManager = new StoreMailboxManager<InMemoryId>(mailboxMapperFactory, new MockAuthenticator(), aclResolver, groupMembershipResolver);
        mailboxManager.init();

        getMailboxesMethod = new GetMailboxesMethod<>(mailboxManager, mailboxMapperFactory);
    }

    @Test
    public void getMailboxesShouldReturnEmptyListWhenNoMailboxes() throws Exception {
        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        GetMailboxesResponse getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, mailboxSession);
        assertThat(getMailboxesResponse.getList()).isEmpty();
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWhenAvailable() throws Exception {
        MailboxPath mailboxPath = new MailboxPath("#private", USERNAME, "name");
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        GetMailboxesResponse getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, mailboxSession);
        assertThat(getMailboxesResponse.getList())
                .extracting(Mailbox::getId, Mailbox::getName, Mailbox::getUnreadMessages)
                .containsOnly(Tuple.tuple("1", mailboxPath.getName(), 2L));
    }

    @Test
    public void getMailboxesShouldReturnInboxWithSortOrder10() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        MailboxPath mailboxPath = new MailboxPath("#private", USERNAME, "INBOX");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        GetMailboxesResponse getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, mailboxSession);
        assertThat(getMailboxesResponse.getList())
                .hasSize(1)
                .extracting(Mailbox::getSortOrder)
                .containsOnly(10);
    }

    @Test
    public void getMailboxesShouldReturnSortOrder1000OnUnknownFolder() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        MailboxPath mailboxPath = new MailboxPath("#private", USERNAME, "unknown");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        GetMailboxesResponse getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, mailboxSession);
        assertThat(getMailboxesResponse.getList())
                .extracting(Mailbox::getSortOrder)
                .containsOnly(1000);
    }

    @Test
    public void getMailboxesShouldReturnInboxWithSortOrder10OnDifferenceCase() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        MailboxPath mailboxPath = new MailboxPath("#private", USERNAME, "InBoX");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        GetMailboxesResponse getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, mailboxSession);
        assertThat(getMailboxesResponse.getList())
                .extracting(Mailbox::getSortOrder)
                .containsOnly(10);
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWithSortOrder() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        mailboxManager.createMailbox(new MailboxPath("#private", USERNAME, "INBOX"), mailboxSession);
        mailboxManager.createMailbox(new MailboxPath("#private", USERNAME, "ARCHIVE"), mailboxSession);
        mailboxManager.createMailbox(new MailboxPath("#private", USERNAME, "DRAFTS"), mailboxSession);
        mailboxManager.createMailbox(new MailboxPath("#private", USERNAME, "OUTBOX"), mailboxSession);
        mailboxManager.createMailbox(new MailboxPath("#private", USERNAME, "SENT"), mailboxSession);
        mailboxManager.createMailbox(new MailboxPath("#private", USERNAME, "TRASH"), mailboxSession);
        mailboxManager.createMailbox(new MailboxPath("#private", USERNAME, "SPAM"), mailboxSession);
        mailboxManager.createMailbox(new MailboxPath("#private", USERNAME, "TEMPLATES"), mailboxSession);

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        GetMailboxesResponse getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, mailboxSession);
        assertThat(getMailboxesResponse.getList())
                .extracting(Mailbox::getName, Mailbox::getSortOrder)
                .containsExactly(
                        Tuple.tuple("INBOX", 10),
                        Tuple.tuple("ARCHIVE", 20),
                        Tuple.tuple("DRAFTS", 30),
                        Tuple.tuple("OUTBOX", 40),
                        Tuple.tuple("SENT", 50),
                        Tuple.tuple("TRASH", 60),
                        Tuple.tuple("SPAM", 70),
                        Tuple.tuple("TEMPLATES", 80));
    }

    @Test
    public void getMailboxesShouldReturnEmptyMailboxByDefault() throws MailboxException {
        MailboxPath mailboxPath = new MailboxPath("#private", USERNAME, "name");
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        GetMailboxesResponse getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, mailboxSession);
        assertThat(getMailboxesResponse.getList())
                .extracting(Mailbox::getTotalMessages, Mailbox::getUnreadMessages)
                .containsOnly(Tuple.tuple(0L, 0L));
    }

    @Test
    public void getMailboxesShouldReturnCorrectTotalMessagesCount() throws MailboxException {
        MailboxPath mailboxPath = new MailboxPath("#private", USERNAME, "name");
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test2\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();

        GetMailboxesResponse getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, mailboxSession);
        assertThat(getMailboxesResponse.getList())
                .extracting(Mailbox::getTotalMessages)
                .containsExactly(2L);
    }

    @Test
    public void getMailboxesShouldReturnCorrectUnreadMessagesCount() throws MailboxException {
        MailboxPath mailboxPath = new MailboxPath("#private", USERNAME, "name");
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME, LOGGER);
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
        GetMailboxesResponse getMailboxesResponse = getMailboxesMethod.process(getMailboxesRequest, mailboxSession);
        assertThat(getMailboxesResponse.getList())
                .extracting(Mailbox::getUnreadMessages)
                .containsExactly(2L);
    }
}