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

package org.apache.james.mailbox.cassandra.mail.task;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageInconsistenciesEntry {

    public interface Builder {
        @FunctionalInterface
        interface RequireMailboxId {
            RequireMessageId mailboxId(String mailboxId);
        }

        @FunctionalInterface
        interface RequireMessageId {
            RequireMessageUid messageId(String messageId);
        }

        @FunctionalInterface
        interface RequireMessageUid {
            MessageInconsistenciesEntry messageUid(Long messageUid);
        }
    }

    public static Builder.RequireMailboxId builder() {
        return mailboxId -> messageId -> messageUid -> new MessageInconsistenciesEntry(mailboxId, messageId, messageUid);
    }

    private final String mailboxId;
    private final String messageId;
    private final Long messageUid;

    private MessageInconsistenciesEntry(@JsonProperty("mailboxId") String mailboxId,
                                        @JsonProperty("messageId") String messageId,
                                       @JsonProperty("uid") Long messageUid) {
        this.mailboxId = mailboxId;
        this.messageId = messageId;
        this.messageUid = messageUid;
    }

    @JsonProperty("mailboxId")
    public String getMailboxId() {
        return mailboxId;
    }

    @JsonProperty("messageId")
    public String getMessageId() {
        return messageId;
    }

    @JsonProperty("uid")
    public Long getMessageUid() {
        return messageUid;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MessageInconsistenciesEntry) {
            MessageInconsistenciesEntry that = (MessageInconsistenciesEntry) o;

            return Objects.equals(this.mailboxId, that.mailboxId)
                && Objects.equals(this.messageId, that.messageId)
                && Objects.equals(this.messageUid, that.messageUid);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(mailboxId, messageId, messageUid);
    }
}
