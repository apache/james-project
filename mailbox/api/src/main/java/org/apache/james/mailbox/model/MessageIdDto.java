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

package org.apache.james.mailbox.model;

import java.util.Objects;

import com.google.common.base.MoreObjects;

public class MessageIdDto {
    private final String messageId;

    public MessageIdDto(String messageId) {
        this.messageId = messageId;
    }

    public MessageIdDto(MessageId messageId) {
        this.messageId = messageId.serialize();
    }

    public MessageId instantiate(MessageId.Factory factory) {
        return factory.fromString(messageId);
    }

    public String asString() {
        return messageId;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MessageIdDto) {
            MessageIdDto that = (MessageIdDto) o;

            return Objects.equals(this.messageId, that.messageId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(messageId);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("messageId", messageId)
            .toString();
    }
}
