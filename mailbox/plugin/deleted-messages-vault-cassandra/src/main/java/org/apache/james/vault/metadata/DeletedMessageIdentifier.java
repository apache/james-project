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

package org.apache.james.vault.metadata;

import java.util.Objects;

import org.apache.james.core.User;
import org.apache.james.mailbox.model.MessageId;

public class DeletedMessageIdentifier {
    private final User owner;
    private final MessageId messageId;

    public DeletedMessageIdentifier(User owner, MessageId messageId) {
        this.owner = owner;
        this.messageId = messageId;
    }

    public User getOwner() {
        return owner;
    }

    public MessageId getMessageId() {
        return messageId;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof DeletedMessageIdentifier) {
            DeletedMessageIdentifier that = (DeletedMessageIdentifier) o;

            return Objects.equals(this.owner, that.owner)
                && Objects.equals(this.messageId, that.messageId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(owner, messageId);
    }
}
