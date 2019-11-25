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
package org.apache.james.pop3server.mailbox;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.protocols.pop3.mailbox.Mailbox;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class MailboxAdapter implements Mailbox {
    private static final FetchGroup FULL_GROUP = FetchGroup.FULL_CONTENT;
    private static final FetchGroup BODY_GROUP = FetchGroup.BODY_CONTENT;
    private static final FetchGroup HEADERS_GROUP = FetchGroup.HEADERS;
    private static final FetchGroup METADATA_GROUP = FetchGroup.MINIMAL;

    private final MessageManager manager;
    private final MailboxSession session;

    private final MailboxManager mailboxManager;

    public MailboxAdapter(MailboxManager mailboxManager, MessageManager manager, MailboxSession session) {
        this.manager = manager;
        this.session = session;
        this.mailboxManager = mailboxManager;
    }

    @Override
    public InputStream getMessageBody(String uid) throws IOException {
        try {
            mailboxManager.startProcessingRequest(session);
            Iterator<MessageResult> results = manager.getMessages(MessageUid.of(Long.parseLong(uid)).toRange(), BODY_GROUP, session);
            if (results.hasNext()) {
                return results.next().getBody().getInputStream();
            } else {
                return null;
            }
        } catch (MailboxException e) {
            throw new IOException("Unable to retrieve message body for uid " + uid, e);
        } finally {
            mailboxManager.endProcessingRequest(session);
        }
    }

    @Override
    public InputStream getMessageHeaders(String uid) throws IOException {
        try {
            mailboxManager.startProcessingRequest(session);
            Iterator<MessageResult> results = manager.getMessages(MessageUid.of(Long.parseLong(uid)).toRange(), HEADERS_GROUP,
                    session);
            if (results.hasNext()) {
                return results.next().getHeaders().getInputStream();
            } else {
                return null;
            }
        } catch (MailboxException e) {
            throw new IOException("Unable to retrieve message header for uid " + uid, e);
        } finally {
            mailboxManager.endProcessingRequest(session);
        }
    }

    @Override
    public InputStream getMessage(String uid) throws IOException {
        try {
            mailboxManager.startProcessingRequest(session);
            Iterator<MessageResult> results = manager.getMessages(MessageUid.of(Long.parseLong(uid)).toRange(), FULL_GROUP, session);
            if (results.hasNext()) {
                return results.next().getFullContent().getInputStream();
            } else {
                return null;
            }
        } catch (MailboxException e) {
            throw new IOException("Unable to retrieve message for uid " + uid, e);
        } finally {
            mailboxManager.endProcessingRequest(session);
        }
    }

    @Override
    public List<MessageMetaData> getMessages() throws IOException {
        try {
            mailboxManager.startProcessingRequest(session);
            Iterator<MessageResult> results = manager.getMessages(MessageRange.all(), METADATA_GROUP, session);
            List<MessageMetaData> mList = new ArrayList<>();
            while (results.hasNext()) {
                MessageResult result = results.next();
                MessageMetaData metaData = new MessageMetaData(String.valueOf(result.getUid().asLong()), result.getSize());
                mList.add(metaData);
            }
            return ImmutableList.copyOf(mList);
        } catch (MailboxException e) {
            throw new IOException("Unable to retrieve messages", e);
        } finally {
            mailboxManager.endProcessingRequest(session);
        }
    }

    @Override
    public void remove(String... uids) throws IOException {
        List<MessageUid> uidList = Arrays.stream(uids)
            .map(uid -> MessageUid.of(Long.parseLong(uid)))
            .collect(Guavate.toImmutableList());

        try {
            mailboxManager.startProcessingRequest(session);
            manager.delete(uidList, session);
        } catch (MailboxException e) {
            String serializedUids = uidList
                .stream()
                .map(uid -> uid.toString())
                .collect(Collectors.joining(",", "[", "]"));

            throw new IOException("Unable to remove messages: " + serializedUids, e);
        } finally {
            mailboxManager.endProcessingRequest(session);
        }
    }

    @Override
    public String getIdentifier() throws IOException {
        try {
            mailboxManager.startProcessingRequest(session);
            long validity = manager.getMailboxEntity()
                    .getUidValidity();
            return Long.toString(validity);
        } catch (MailboxException e) {
            throw new IOException("Unable to retrieve indentifier for mailbox", e);
        } finally {
            mailboxManager.endProcessingRequest(session);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            mailboxManager.logout(session, true);
        } catch (MailboxException e) {
            throw new IOException("Unable to close mailbox", e);
        } finally {
            mailboxManager.endProcessingRequest(session);
        }
    }
}
