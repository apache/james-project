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
package org.apache.james.jmap.model;

import java.util.Objects;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSession.User;
import org.apache.james.mailbox.model.MailboxPath;
import org.javatuples.Triplet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

public class MessageId {

    private static final String SEPARATOR = "|";

    @JsonCreator
    public static MessageId of(String id) {
        Triplet<String, String, String> parts = Triplet.fromIterable(Splitter.on(SEPARATOR).split(id));
        return new MessageId(parts.getValue0(), parts.getValue1(), Long.valueOf(parts.getValue2()));
    }

    private final String mailboxPath;
    private final long uid;
    private final String username;

    public MessageId(User username, MailboxPath mailboxPath, long uid) {
        this.username = username.getUserName();
        this.mailboxPath = mailboxPath.getName();
        this.uid = uid;
    }
    
    @VisibleForTesting
    MessageId(String username, String mailboxPath, long uid) {
        this.username = username;
        this.mailboxPath = mailboxPath;
        this.uid = uid;
    }
    
    public String getUsername() {
        return username;
    }
    
    public long getUid() {
        return uid;
    }
    
    public MailboxPath getMailboxPath(MailboxSession mailboxSession) {
        return new MailboxPath("", username, mailboxPath);
    }
    
    @JsonValue
    public String serialize() {
        return Joiner.on(SEPARATOR).join(username, mailboxPath, uid);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MessageId) {
            MessageId other = (MessageId) obj;
            return Objects.equals(username, other.username)
                 && Objects.equals(mailboxPath, other.mailboxPath)
                 && Objects.equals(uid, other.uid);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, mailboxPath, uid);
    }
    
    @Override
    public String toString() {
        return com.google.common.base.Objects
                .toStringHelper(getClass())
                .add("username", username)
                .add("mailboxPath", mailboxPath)
                .add("uid", uid)
                .toString();
    }
}
