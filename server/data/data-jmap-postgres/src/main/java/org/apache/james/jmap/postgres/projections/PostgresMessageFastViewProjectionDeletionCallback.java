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

package org.apache.james.jmap.postgres.projections;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.postgres.DeleteMessageListener;
import org.apache.james.mailbox.postgres.mail.MessageRepresentation;

import reactor.core.publisher.Mono;

public class PostgresMessageFastViewProjectionDeletionCallback implements DeleteMessageListener.DeletionCallback {
    private final MessageFastViewProjection messageFastViewProjection;

    @Inject
    public PostgresMessageFastViewProjectionDeletionCallback(MessageFastViewProjection messageFastViewProjection) {
        this.messageFastViewProjection = messageFastViewProjection;
    }

    @Override
    public Mono<Void> forMessage(MessageRepresentation messageRepresentation, MailboxId mailboxId, Username owner) {
        return Mono.from(messageFastViewProjection.delete(messageRepresentation.getMessageId()));
    }
}
