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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.mail.Flags;

import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.GetMailboxesRequest;
import org.apache.james.jmap.model.GetMailboxesResponse;
import org.apache.james.jmap.model.MailboxFactory;
import org.apache.james.jmap.model.Number;
import org.apache.james.jmap.model.mailbox.Mailbox;
import org.apache.james.jmap.model.mailbox.SortOrder;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class GetMailboxesMethodTest {

    private static final String USERNAME = "username@domain.tld";
    private static final String USERNAME2 = "username2@domain.tld";

    private StoreMailboxManager mailboxManager;
    private GetMailboxesMethod getMailboxesMethod;
    private ClientId clientId;
    private MailboxFactory mailboxFactory;

    private QuotaRootResolver quotaRootResolver;
    private QuotaManager quotaManager;

    @Before
    public void setup() throws Exception {
        clientId = ClientId.of("#0");
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        quotaRootResolver = mailboxManager.getQuotaComponents().getQuotaRootResolver();
        quotaManager = mailboxManager.getQuotaComponents().getQuotaManager();
        mailboxFactory = new MailboxFactory(mailboxManager, quotaManager, quotaRootResolver);

        getMailboxesMethod = new GetMailboxesMethod(mailboxManager, quotaRootResolver, quotaManager,  mailboxFactory, new DefaultMetricFactory());
    }

    @Test
    public void getMailboxesShouldReturnEmptyListWhenNoMailboxes() {
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
        StoreMailboxManager mockedMailboxManager = mock(StoreMailboxManager.class);
        when(mockedMailboxManager.list(any()))
            .thenReturn(ImmutableList.of(new MailboxPath("namespace", "user", "name")));
        when(mockedMailboxManager.getMailbox(any(MailboxPath.class), any()))
            .thenThrow(new MailboxException());
        GetMailboxesMethod testee = new GetMailboxesMethod(mockedMailboxManager, quotaRootResolver, quotaManager, mailboxFactory, new DefaultMetricFactory());

        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .build();
        MailboxSession session = MailboxSessionUtil.create(USERNAME);

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
    @SuppressWarnings("unchecked")
    public void getMailboxesShouldReturnMailboxesWhenAvailable() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, "name");
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
        messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setSubject("test")
                .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession);
        messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setSubject("test2")
                .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession);

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
                .containsOnly(Tuple.tuple(InMemoryId.of(1), mailboxPath.getName(), Number.fromLong(2L)));
    }

    @Test
    @SuppressWarnings("unchecked")
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
    @SuppressWarnings("unchecked")
    public void getMailboxesShouldReturnMailboxesWithSortOrder() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "INBOX"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "Archive"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "Drafts"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "Outbox"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "Sent"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "Trash"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "Spam"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "Templates"), mailboxSession);

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
                        Tuple.tuple("Archive", SortOrder.of(20)),
                        Tuple.tuple("Drafts", SortOrder.of(30)),
                        Tuple.tuple("Outbox", SortOrder.of(40)),
                        Tuple.tuple("Sent", SortOrder.of(50)),
                        Tuple.tuple("Trash", SortOrder.of(60)),
                        Tuple.tuple("Spam", SortOrder.of(70)),
                        Tuple.tuple("Templates", SortOrder.of(80)));
    }

    @Test
    @SuppressWarnings("unchecked")
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
                .containsOnly(Tuple.tuple(Number.ZERO, Number.ZERO));
    }

    @Test
    public void getMailboxesShouldReturnCorrectTotalMessagesCount() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, "name");
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
        messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setSubject("test")
                .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession);
        messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setSubject("test2")
                .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession);

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
                .containsExactly(Number.fromLong(2L));
    }

    @Test
    public void getMailboxesShouldReturnCorrectUnreadMessagesCount() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, "name");
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
        Flags defaultUnseenFlag = new Flags();
        Flags readMessageFlag = new Flags();
        readMessageFlag.add(Flags.Flag.SEEN);
        messageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withFlags(defaultUnseenFlag)
            .build(Message.Builder.of()
                .setSubject("test")
                .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession);
        messageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withFlags(defaultUnseenFlag)
            .build(Message.Builder.of()
                .setSubject("test2")
                .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession);
        messageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withFlags(readMessageFlag)
            .build(Message.Builder.of()
                .setSubject("test3")
                .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession);
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
                .containsExactly(Number.fromLong(2L));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getMailboxesShouldReturnMailboxesWithRoles() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "INBOX"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "Archive"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "Drafts"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "Outbox"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "Sent"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "Trash"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "Spam"), mailboxSession);
        mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, "Templates"), mailboxSession);
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
                        Tuple.tuple("Archive", Optional.of(Role.ARCHIVE)),
                        Tuple.tuple("Drafts", Optional.of(Role.DRAFTS)),
                        Tuple.tuple("Outbox", Optional.of(Role.OUTBOX)),
                        Tuple.tuple("Sent", Optional.of(Role.SENT)),
                        Tuple.tuple("Trash", Optional.of(Role.TRASH)),
                        Tuple.tuple("Spam", Optional.of(Role.SPAM)),
                        Tuple.tuple("Templates", Optional.of(Role.TEMPLATES)),
                        Tuple.tuple("WITHOUT ROLE", Optional.empty()));
    }
}
