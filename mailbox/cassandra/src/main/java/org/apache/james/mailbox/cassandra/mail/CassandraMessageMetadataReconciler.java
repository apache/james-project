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

import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;

import reactor.core.publisher.Mono;

/** Repairs historical header references after a missing-blob read, using messageV3 as the source of truth. */
class CassandraMessageMetadataReconciler {
    private final CassandraMessageDAOV3 messages;
    private final CassandraMessageIdDAO messageIds;
    private final CassandraMessageIdToImapUidDAO imapUids;

    CassandraMessageMetadataReconciler(CassandraMessageDAOV3 messages, CassandraMessageIdDAO messageIds,
                                      CassandraMessageIdToImapUidDAO imapUids) {
        this.messages = messages;
        this.messageIds = messageIds;
        this.imapUids = imapUids;
    }

    Mono<MessageRepresentation> reconcile(CassandraMessageMetadata metadata) {
        return messages.retrieveMessage(metadata.getComposedMessageId(), FetchType.HEADERS)
            .flatMap(message -> repair(metadata, message.getHeaderId()).thenReturn(message));
    }

    private Mono<Void> repair(CassandraMessageMetadata metadata, BlobId headerId) {
        ComposedMessageId id = metadata.getComposedMessageId().getComposedMessageId();
        BlobId oldId = metadata.getHeaderContent().orElseThrow();
        // Compare-and-set avoids recreating deleted COPY/MOVE rows or overwriting a newer repair.
        return messageIds.replaceHeaderContent(id, oldId, headerId)
            .then(imapUids.replaceHeaderContent(id, oldId, headerId));
    }
}
