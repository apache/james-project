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
package org.apache.james.protocols.pop3.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.protocols.pop3.mailbox.ImapMailbox;
import org.apache.james.protocols.pop3.mailbox.ImapMessageMetaData;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;

import com.github.steveash.guavate.Guavate;

@SuppressWarnings("deprecation")
public class MockMailbox extends ImapMailbox {

    private final Map<Long, Message> messages = new HashMap<Long, Message>();
    private final String identifier;

    public MockMailbox(String identifier, Message... messages) {
        this.identifier = identifier;
        for (Message m: messages) {
            this.messages.put(Long.parseLong(m.meta.getUid()), m);
        }
    }
    
    public MockMailbox(String identifier) {
        this(identifier, new Message[0]);
    }

    public InputStream getMessageBody(long uid) throws IOException {
        Message m = messages.get(uid);
        if (m == null) {
            return null;
        }
        return new ByteArrayInputStream(m.body.getBytes("US-ASCII"));
    }

    public InputStream getMessageHeaders(long uid) throws IOException {
        Message m = messages.get(uid);
        if (m == null) {
            return null;
        }
        return new ByteArrayInputStream((m.headers + "\r\n").getBytes("US-ASCII"));
    }

    public InputStream getMessage(long uid) throws IOException {
        InputStream body = getMessageBody(uid);
        InputStream headers = getMessageHeaders(uid);
        if (body == null || headers == null) {
            return null;
        }
        return new SequenceInputStream(headers, body);
    }

    public List<MessageMetaData> getMessages() throws IOException {
        return messages.values()
            .stream()
            .map(m -> m.meta)
            .collect(Guavate.toImmutableList());
    }

    public void remove(long... uids) throws IOException {
        for (long uid: uids) {
            messages.remove(uid);
        }
    }

    public String getIdentifier() throws IOException {
        return identifier;
    }

    public void close() throws IOException {
        // nothing
    }
    
    public static final class Message {
        private static final AtomicLong UIDS = new AtomicLong(0);
        public final String headers;
        public final String body;
        public final MessageMetaData meta;

        public Message(String headers, String body) {
            this.headers = headers;
            this.body = body;
            this.meta = new ImapMessageMetaData(UIDS.incrementAndGet(), headers.length() + body.length() + 2);
        }
        
        public String toString() {
            return headers + "\r\n" + body;
        }
        
    }
    
}


