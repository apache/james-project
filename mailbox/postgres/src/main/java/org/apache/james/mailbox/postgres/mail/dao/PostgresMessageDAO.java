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

import static org.apache.james.backends.postgres.PostgresCommons.DATE_TO_LOCAL_DATE_TIME;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.BLOB_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.BODY_START_OCTET;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_DESCRIPTION;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_DISPOSITION_PARAMETERS;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_DISPOSITION_TYPE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_LANGUAGE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_LOCATION;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_MD5;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_TRANSFER_ENCODING;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_TYPE_PARAMETERS;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.HEADER_CONTENT;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.INTERNAL_DATE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.MESSAGE_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.MIME_SUBTYPE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.MIME_TYPE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.SIZE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.TABLE_NAME;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.TEXTUAL_LINE_COUNT;

import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.jooq.postgres.extensions.types.Hstore;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class PostgresMessageDAO {
    public static final long DEFAULT_LONG_VALUE = 0L;
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
                .set(TEXTUAL_LINE_COUNT, Optional.ofNullable(message.getTextualLineCount()).orElse(DEFAULT_LONG_VALUE).intValue())
                .set(CONTENT_DESCRIPTION, message.getProperties().getContentDescription())
                .set(CONTENT_DISPOSITION_TYPE, message.getProperties().getContentDispositionType())
                .set(CONTENT_ID, message.getProperties().getContentID())
                .set(CONTENT_MD5, message.getProperties().getContentMD5())
                .set(CONTENT_LANGUAGE, message.getProperties().getContentLanguage().toArray(new String[0]))
                .set(CONTENT_LOCATION, message.getProperties().getContentLocation())
                .set(CONTENT_TRANSFER_ENCODING, message.getProperties().getContentTransferEncoding())
                .set(CONTENT_TYPE_PARAMETERS, Hstore.hstore(message.getProperties().getContentTypeParameters()))
                .set(CONTENT_DISPOSITION_PARAMETERS, Hstore.hstore(message.getProperties().getContentDispositionParameters()))
                .set(HEADER_CONTENT, headerContentAsByte))));
    }

    public Mono<Void> deleteByMessageId(PostgresMessageId messageId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(MESSAGE_ID.eq(messageId.asUuid()))));
    }

}
