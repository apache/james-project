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

package org.apache.james.mailbox.postgres.mail.dao;

import static org.apache.james.mailbox.postgres.mail.PostgresCommons.DATE_TO_LOCAL_DATE_TIME;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.BLOB_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.BODY_START_OCTET;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.HEADER_CONTENT;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.INTERNAL_DATE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.MESSAGE_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.MIME_SUBTYPE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.MIME_TYPE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.SIZE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.TABLE_NAME;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class PostgresMessageDAO {
    private final PostgresExecutor postgresExecutor;

    public PostgresMessageDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<Void> insert(MailboxMessage message, String blobId) {
        return Mono.fromCallable(() -> IOUtils.toByteArray(message.getHeaderContent(), message.getHeaderOctets()))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(headerContentAsByte -> postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
                .set(MESSAGE_ID, ((PostgresMessageId) message.getMessageId()).asUuid())
                .set(BLOB_ID, blobId)
                .set(MIME_TYPE, message.getMediaType())
                .set(MIME_SUBTYPE, message.getSubType())
                .set(INTERNAL_DATE, DATE_TO_LOCAL_DATE_TIME.apply(message.getInternalDate()))
                .set(SIZE, message.getFullContentOctets())
                .set(BODY_START_OCTET, (int) (message.getFullContentOctets() - message.getBodyOctets()))
                .set(HEADER_CONTENT, headerContentAsByte))));
    }

    public Mono<Void> deleteByMessageId(PostgresMessageId messageId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(MESSAGE_ID.eq(messageId.asUuid()))));
    }

}
