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

package org.apache.james.jmap.send;

import java.util.Objects;

import org.apache.james.mailbox.model.MessageId;
import org.apache.mailet.AttributeName;

import com.google.common.base.Preconditions;

public class MailMetadata {
    public static final AttributeName MAIL_METADATA_MESSAGE_ID_ATTRIBUTE = AttributeName.of("org.apache.james.jmap.send.MailMetaData.messageId");
    public static final AttributeName MAIL_METADATA_USERNAME_ATTRIBUTE = AttributeName.of("org.apache.james.jmap.send.MailMetaData.username");

    private final MessageId messageId;
    private final String username;

    public MailMetadata(MessageId messageId, String username) {
        Preconditions.checkNotNull(messageId);
        Preconditions.checkNotNull(username);
        this.messageId = messageId;
        this.username = username;
    }

    public MessageId getMessageId() {
        return messageId;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MailMetadata) {
            MailMetadata other = (MailMetadata) obj;
            return Objects.equals(this.messageId, other.messageId)
                && Objects.equals(this.username, other.username);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, username);
    }
}
