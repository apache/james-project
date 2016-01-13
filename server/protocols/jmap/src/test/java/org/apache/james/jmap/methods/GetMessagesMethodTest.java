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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.GetMessagesRequest;
import org.apache.james.jmap.model.GetMessagesResponse;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.MessageProperty;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;

public class GetMessagesMethodTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetMessagesMethodTest.class);
    
    private static class User implements org.apache.james.mailbox.MailboxSession.User {
        final String username;
        final String password;

        public User(String username, String password) {
            this.username = username;
            this.password = password;
        }
        
        @Override
        public String getUserName() {
            return username;
        }

        @Override
        public String getPassword() {
            return password;
        }
        
        @Override
        public List<Locale> getLocalePreferences() {
            return ImmutableList.of();
        }
    }
    
    private static final User ROBERT = new User("robert", "secret");

    private StoreMailboxManager<InMemoryId> mailboxManager;
    private InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory;

    private MailboxSession session;
    private MailboxPath inboxPath;
    private ClientId clientId;

    @Before
    public void setup() throws MailboxException {
        clientId = ClientId.of("#0");
        mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
        MockAuthenticator authenticator = new MockAuthenticator();
        authenticator.addUser(ROBERT.username, ROBERT.password);
        UnionMailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        SimpleGroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        mailboxManager = new StoreMailboxManager<>(mailboxSessionMapperFactory, authenticator, aclResolver, groupMembershipResolver);
        mailboxManager.init();
        

        session = mailboxManager.login(ROBERT.username, ROBERT.password, LOGGER);
        inboxPath = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inboxPath, session);
    }
    
    @Test
    public void processShouldThrowWhenNullRequest() {
        GetMessagesMethod<InMemoryId> testee = new GetMessagesMethod<>(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        GetMessagesRequest request = null;
        assertThatThrownBy(() -> testee.process(request, mock(ClientId.class), mock(MailboxSession.class))).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenNullSession() {
        GetMessagesMethod<InMemoryId> testee = new GetMessagesMethod<>(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        MailboxSession mailboxSession = null;
        assertThatThrownBy(() -> testee.process(mock(GetMessagesRequest.class), mock(ClientId.class), mailboxSession)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenNullClientId() {
        GetMessagesMethod<InMemoryId> testee = new GetMessagesMethod<>(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        ClientId clientId = null;
        assertThatThrownBy(() -> testee.process(mock(GetMessagesRequest.class), clientId, mock(MailboxSession.class))).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void processShouldThrowWhenRequestHasAccountId() {
        GetMessagesMethod<InMemoryId> testee = new GetMessagesMethod<>(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        assertThatThrownBy(() -> testee.process(
                GetMessagesRequest.builder().accountId("abc").build(), mock(ClientId.class), mock(MailboxSession.class))).isInstanceOf(NotImplementedException.class);
    }
    
    @Test
    public void processShouldFetchMessages() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        Date now = new Date();
        ByteArrayInputStream message1Content = new ByteArrayInputStream("Subject: message 1 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        long message1Uid = inbox.appendMessage(message1Content, now, session, false, null);
        ByteArrayInputStream message2Content = new ByteArrayInputStream("Subject: message 2 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        long message2Uid = inbox.appendMessage(message2Content, now, session, false, null);
        ByteArrayInputStream message3Content = new ByteArrayInputStream("Great-Header: message 3 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        long message3Uid = inbox.appendMessage(message3Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(new MessageId(ROBERT, inboxPath, message1Uid),
                          new MessageId(ROBERT, inboxPath, message2Uid),
                          new MessageId(ROBERT, inboxPath, message3Uid))
                .build();

        GetMessagesMethod<InMemoryId> testee = new GetMessagesMethod<>(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());
        
        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getResponse)
            .hasOnlyElementsOfType(GetMessagesResponse.class)
            .extracting(GetMessagesResponse.class::cast)
            .flatExtracting(GetMessagesResponse::list)
            .extracting(message -> message.getId().getUid(), Message::getSubject)
            .containsOnly(
                    Tuple.tuple(message1Uid, "message 1 subject"), 
                    Tuple.tuple(message2Uid, "message 2 subject"),
                    Tuple.tuple(message3Uid, "(No subject)"));
    }

    @Test
    public void processShouldReturnOnlyMessageIdsOnEmptyPropertyList() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        Date now = new Date();
        ByteArrayInputStream message1Content = new ByteArrayInputStream("Subject: message 1 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        long message1Uid = inbox.appendMessage(message1Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(new MessageId(ROBERT, inboxPath, message1Uid))
                .properties(new MessageProperty[0])
                .build();

        GetMessagesMethod<InMemoryId> testee = new GetMessagesMethod<>(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getProperties)
            .flatExtracting(Optional::get)
            .asList()
            .containsOnly(MessageProperty.id);
    }

    @Test
    public void processShouldReturnIdWhenNotInPropertyList() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        Date now = new Date();
        ByteArrayInputStream message1Content = new ByteArrayInputStream("Subject: message 1 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        long message1Uid = inbox.appendMessage(message1Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(new MessageId(ROBERT, inboxPath, message1Uid))
                .properties(MessageProperty.subject)
                .build();

        GetMessagesMethod<InMemoryId> testee = new GetMessagesMethod<>(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getProperties)
            .flatExtracting(Optional::get)
            .asList()
            .containsOnly(MessageProperty.id, MessageProperty.subject);
    }
    
    @Test
    public void processShouldReturnTextBodyWhenBodyInPropertyListAndEmptyHtmlBody() throws MailboxException {
        MessageManager inbox = mailboxManager.getMailbox(inboxPath, session);
        Date now = new Date();
        ByteArrayInputStream message1Content = new ByteArrayInputStream("Subject: message 1 subject\r\n\r\nmy message".getBytes(Charsets.UTF_8));
        long message1Uid = inbox.appendMessage(message1Content, now, session, false, null);
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .ids(new MessageId(ROBERT, inboxPath, message1Uid))
                .properties(MessageProperty.body)
                .build();

        GetMessagesMethod<InMemoryId> testee = new GetMessagesMethod<>(mailboxSessionMapperFactory, mailboxSessionMapperFactory);
        List<JmapResponse> result = testee.process(request, clientId, session).collect(Collectors.toList());

        assertThat(result).hasSize(1)
            .extracting(JmapResponse::getProperties)
            .flatExtracting(Optional::get)
            .asList()
            .containsOnly(MessageProperty.id, MessageProperty.textBody);
    }
}
