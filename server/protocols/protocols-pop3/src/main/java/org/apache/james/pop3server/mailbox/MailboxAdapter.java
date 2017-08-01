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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResult.FetchGroup;
import org.apache.james.protocols.pop3.mailbox.Mailbox;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;

import com.github.steveash.guavate.Guavate;

public class MailboxAdapter implements Mailbox {

    private static abstract class POP3FetchGroup implements FetchGroup {
        @Override
        public Set<PartContentDescriptor> getPartContentDescriptors() {
            return new HashSet<PartContentDescriptor>();
        }
    }

    private final static FetchGroup FULL_GROUP = new POP3FetchGroup() {

        @Override
        public int content() {
            return BODY_CONTENT | HEADERS;
        }

    };

    private final static FetchGroup BODY_GROUP = new POP3FetchGroup() {

        @Override
        public int content() {
            return BODY_CONTENT;
        }

    };

    private final static FetchGroup HEADERS_GROUP = new POP3FetchGroup() {

        @Override
        public int content() {
            return HEADERS;
        }
    };

    private final static FetchGroup METADATA_GROUP = new POP3FetchGroup() {

        @Override
        public int content() {
            return MINIMAL;
        }
    };

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
            Iterator<MessageResult> results = manager.getMessages(MessageUid.of(Long.valueOf(uid)).toRange(), BODY_GROUP, session);
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
            Iterator<MessageResult> results = manager.getMessages(MessageUid.of(Long.valueOf(uid)).toRange(), HEADERS_GROUP,
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
            Iterator<MessageResult> results = manager.getMessages(MessageUid.of(Long.valueOf(uid)).toRange(), FULL_GROUP, session);
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
            List<MessageMetaData> mList = new ArrayList<MessageMetaData>();
            while (results.hasNext()) {
                MessageResult result = results.next();
                MessageMetaData metaData = new MessageMetaData(String.valueOf(result.getUid().asLong()), result.getSize());
                mList.add(metaData);
            }
            return Collections.unmodifiableList(mList);
        } catch (MailboxException e) {
            throw new IOException("Unable to retrieve messages", e);
        } finally {
            mailboxManager.endProcessingRequest(session);
        }
    }

    @Override
    public void remove(String... uids) throws IOException {
        List<MessageUid> uidList = Arrays.stream(uids)
            .map(uid -> MessageUid.of(Long.valueOf(uid)))
            .collect(Guavate.toImmutableList());

        List<MessageRange> ranges = MessageRange.toRanges(uidList);
        try {
            mailboxManager.startProcessingRequest(session);
            for (MessageRange range : ranges) {
                manager.setFlags(new Flags(Flags.Flag.DELETED), MessageManager.FlagsUpdateMode.ADD, range, session);
                manager.expunge(range, session);
            }
        } catch (MailboxException e) {
            throw new IOException("Unable to remove messages for ranges " + ranges);
        } finally {
            mailboxManager.endProcessingRequest(session);
        }
    }

    @Override
    public String getIdentifier() throws IOException {
        try {
            mailboxManager.startProcessingRequest(session);
            long validity = manager.getMetaData(false, session, MessageManager.MetaData.FetchGroup.NO_COUNT)
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
