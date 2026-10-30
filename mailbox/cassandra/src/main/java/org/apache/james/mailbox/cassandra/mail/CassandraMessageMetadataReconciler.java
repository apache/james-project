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

package org.apache.james.mailbox.cassandra.mail;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Handles applicative read-repairs for Cassandra denormalized message header tables
 * when metadata inconsistencies or compacted blob relocations are encountered on the read path.
 */
public final class CassandraMessageMetadataReconciler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraMessageMetadataReconciler.class);

    private CassandraMessageMetadataReconciler() {
    }

    public static Mono<Void> reconcileDenormalizedHeaders(CassandraMessageIdToImapUidDAO imapUidDAO,
                                                         CassandraMessageIdDAO messageIdDAO,
                                                         CassandraId mailboxId,
                                                         MessageUid uid,
                                                         CassandraMessageId messageId,
                                                         MessageRepresentation representation) {
        Mono<Void> updateImapUid = imapUidDAO.updateDenormalizedFields(
            messageId, mailboxId, uid, representation.getInternalDate(),
            representation.getBodyStartOctet(), representation.getSize(), representation.getHeaderId());
        Mono<Void> updateMessageId = messageIdDAO.updateDenormalizedFields(
            mailboxId, uid, representation.getInternalDate(),
            representation.getBodyStartOctet(), representation.getSize(), representation.getHeaderId());

        return Flux.merge(updateImapUid, updateMessageId)
            .then()
            .onErrorResume(e -> {
                LOGGER.warn("Failed to reconcile denormalized blob headers for message {}", messageId.serialize(), e);
                return Mono.empty();
            });
    }
}
