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

import static org.apache.james.backends.postgres.PostgresCommons.LOCAL_DATE_TIME_DATE_FUNCTION;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.BODY_START_OCTET;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_DISPOSITION_PARAMETERS;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_LANGUAGE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_LOCATION;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_MD5;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_TRANSFER_ENCODING;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.CONTENT_TYPE_PARAMETERS;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.HEADER_CONTENT;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.INTERNAL_DATE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.SIZE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.IS_ANSWERED;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.IS_DELETED;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.IS_DRAFT;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.IS_FLAGGED;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.IS_RECENT;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.IS_SEEN;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.MAILBOX_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.MESSAGE_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.MESSAGE_UID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.MOD_SEQ;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.SAVE_DATE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.THREAD_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.USER_FLAGS;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.PostgresMessageModule;
import org.apache.james.mailbox.store.mail.model.impl.Properties;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.jooq.Field;
import org.jooq.Record;

interface PostgresMailboxMessageDAOUtils {
    Map<Field<Boolean>, Flags.Flag> BOOLEAN_FLAGS_MAPPING = Map.of(
        IS_ANSWERED, Flags.Flag.ANSWERED,
        IS_DELETED, Flags.Flag.DELETED,
        IS_DRAFT, Flags.Flag.DRAFT,
        IS_FLAGGED, Flags.Flag.FLAGGED,
        IS_RECENT, Flags.Flag.RECENT,
        IS_SEEN, Flags.Flag.SEEN);
    Function<Record, MessageUid> RECORD_TO_MESSAGE_UID_FUNCTION = record -> MessageUid.of(record.get(MESSAGE_UID));
    Function<Record, Flags> RECORD_TO_FLAGS_FUNCTION = record -> {
        Flags flags = new Flags();
        BOOLEAN_FLAGS_MAPPING.forEach((flagColumn, flagMapped) -> {
            if (record.get(flagColumn)) {
                flags.add(flagMapped);
            }
        });

        Optional.ofNullable(record.get(USER_FLAGS)).stream()
            .flatMap(Arrays::stream)
            .forEach(flags::add);
        return flags;
    };

    Function<Record, ThreadId> RECORD_TO_THREAD_ID_FUNCTION = record -> Optional.ofNullable(record.get(THREAD_ID))
        .map(threadIdAsUuid -> ThreadId.fromBaseMessageId(PostgresMessageId.Factory.of(threadIdAsUuid)))
        .orElse(ThreadId.fromBaseMessageId(PostgresMessageId.Factory.of(record.get(MESSAGE_ID))));


    Field<?>[] MESSAGE_METADATA_FIELDS_REQUIRE = new Field[] {
        MESSAGE_UID,
        MOD_SEQ,
        SIZE,
        INTERNAL_DATE,
        SAVE_DATE,
        MESSAGE_ID,
        THREAD_ID,
        IS_ANSWERED,
        IS_DELETED,
        IS_DRAFT,
        IS_FLAGGED,
        IS_RECENT,
        IS_SEEN,
        USER_FLAGS
    };

    Function<Record, MessageMetaData> RECORD_TO_MESSAGE_METADATA_FUNCTION = record ->
        new MessageMetaData(MessageUid.of(record.get(MESSAGE_UID)),
            ModSeq.of(record.get(MOD_SEQ)),
            RECORD_TO_FLAGS_FUNCTION.apply(record),
            record.get(SIZE),
            LOCAL_DATE_TIME_DATE_FUNCTION.apply(record.get(INTERNAL_DATE)),
            Optional.ofNullable(record.get(SAVE_DATE)).map(LOCAL_DATE_TIME_DATE_FUNCTION),
            PostgresMessageId.Factory.of(record.get(MESSAGE_ID)),
            RECORD_TO_THREAD_ID_FUNCTION.apply(record));

    Function<Record, ComposedMessageIdWithMetaData> RECORD_TO_COMPOSED_MESSAGE_ID_WITH_META_DATA_FUNCTION = record -> ComposedMessageIdWithMetaData
        .builder()
        .composedMessageId(new ComposedMessageId(PostgresMailboxId.of(record.get(MAILBOX_ID)),
            PostgresMessageId.Factory.of(record.get(MESSAGE_ID)),
            MessageUid.of(record.get(MESSAGE_UID))))
        .threadId(RECORD_TO_THREAD_ID_FUNCTION.apply(record))
        .flags(RECORD_TO_FLAGS_FUNCTION.apply(record))
        .modSeq(ModSeq.of(record.get(MOD_SEQ)))
        .build();

    Function<Record, Properties> RECORD_TO_PROPERTIES_FUNCTION = record -> {
        PropertyBuilder property = new PropertyBuilder();

        property.setMediaType(record.get(PostgresMessageModule.MessageTable.MIME_TYPE));
        property.setSubType(record.get(PostgresMessageModule.MessageTable.MIME_SUBTYPE));
        property.setTextualLineCount(Optional.ofNullable(record.get(PostgresMessageModule.MessageTable.TEXTUAL_LINE_COUNT))
            .map(Long::valueOf)
            .orElse(null));

        property.setContentID(record.get(CONTENT_ID));
        property.setContentMD5(record.get(CONTENT_MD5));
        property.setContentTransferEncoding(record.get(CONTENT_TRANSFER_ENCODING));
        property.setContentLocation(record.get(CONTENT_LOCATION));
        property.setContentLanguage(Optional.ofNullable(record.get(CONTENT_LANGUAGE)).map(List::of).orElse(null));
        property.setContentDispositionParameters(record.get(CONTENT_DISPOSITION_PARAMETERS, LinkedHashMap.class));
        property.setContentTypeParameters(record.get(CONTENT_TYPE_PARAMETERS, LinkedHashMap.class));
        return property.build();
    };

    Function<byte[], Content> BYTE_TO_CONTENT_FUNCTION = contentAsBytes -> new Content() {
        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(contentAsBytes);
        }

        @Override
        public long size() {
            return contentAsBytes.length;
        }
    };

    Function<Record, SimpleMailboxMessage.Builder> RECORD_TO_MAILBOX_MESSAGE_BUILDER_FUNCTION = record -> SimpleMailboxMessage.builder()
        .messageId(PostgresMessageId.Factory.of(record.get(MESSAGE_ID)))
        .mailboxId(PostgresMailboxId.of(record.get(MAILBOX_ID)))
        .uid(MessageUid.of(record.get(MESSAGE_UID)))
        .threadId(RECORD_TO_THREAD_ID_FUNCTION.apply(record))
        .internalDate(LOCAL_DATE_TIME_DATE_FUNCTION.apply(record.get(PostgresMessageModule.MessageTable.INTERNAL_DATE, LocalDateTime.class)))
        .saveDate(LOCAL_DATE_TIME_DATE_FUNCTION.apply(record.get(SAVE_DATE, LocalDateTime.class)))
        .flags(RECORD_TO_FLAGS_FUNCTION.apply(record))
        .size(record.get(PostgresMessageModule.MessageTable.SIZE))
        .bodyStartOctet(record.get(BODY_START_OCTET))
        .content(BYTE_TO_CONTENT_FUNCTION.apply(record.get(HEADER_CONTENT)))
        .properties(RECORD_TO_PROPERTIES_FUNCTION.apply(record));


}
