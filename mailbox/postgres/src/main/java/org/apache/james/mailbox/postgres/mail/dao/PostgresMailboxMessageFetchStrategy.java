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
import static org.apache.james.backends.postgres.PostgresCommons.tableField;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.BODY_START_OCTET;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.HEADER_CONTENT;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.MAILBOX_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.MESSAGE_UID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.MOD_SEQ;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.SAVE_DATE;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.BYTE_TO_CONTENT_FUNCTION;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.RECORD_TO_FLAGS_FUNCTION;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.RECORD_TO_PROPERTIES_FUNCTION;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.RECORD_TO_THREAD_ID_FUNCTION;

import java.time.LocalDateTime;
import java.util.function.Function;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.PostgresMessageModule;
import org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.jooq.Field;
import org.jooq.Record;

public interface PostgresMailboxMessageFetchStrategy {
    PostgresMailboxMessageFetchStrategy METADATA = new MetaData();
    PostgresMailboxMessageFetchStrategy FULL = new Full();

    Field<?>[] fetchFields();

    Function<Record, SimpleMailboxMessage.Builder> toMessageBuilder();

    static Function<Record, SimpleMailboxMessage.Builder> toMessageBuilderMetadata() {
        return record -> SimpleMailboxMessage.builder()
            .messageId(PostgresMessageId.Factory.of(record.get(MessageTable.MESSAGE_ID)))
            .mailboxId(PostgresMailboxId.of(record.get(MAILBOX_ID)))
            .uid(MessageUid.of(record.get(MESSAGE_UID)))
            .modseq(ModSeq.of(record.get(MOD_SEQ)))
            .threadId(RECORD_TO_THREAD_ID_FUNCTION.apply(record))
            .internalDate(LOCAL_DATE_TIME_DATE_FUNCTION.apply(record.get(PostgresMessageModule.MessageTable.INTERNAL_DATE, LocalDateTime.class)))
            .saveDate(LOCAL_DATE_TIME_DATE_FUNCTION.apply(record.get(SAVE_DATE, LocalDateTime.class)))
            .flags(RECORD_TO_FLAGS_FUNCTION.apply(record))
            .size(record.get(PostgresMessageModule.MessageTable.SIZE))
            .bodyStartOctet(record.get(BODY_START_OCTET));
    }

    static Field<?>[] fetchFieldsMetadata() {
        return new Field[]{
            tableField(MessageTable.TABLE_NAME, MessageTable.MESSAGE_ID).as(MessageTable.MESSAGE_ID),
            tableField(MessageTable.TABLE_NAME, MessageTable.INTERNAL_DATE).as(MessageTable.INTERNAL_DATE),
            tableField(MessageTable.TABLE_NAME, MessageTable.SIZE).as(MessageTable.SIZE),
            MessageTable.BLOB_ID,
            MessageTable.MIME_TYPE,
            MessageTable.MIME_SUBTYPE,
            MessageTable.BODY_START_OCTET,
            MessageTable.TEXTUAL_LINE_COUNT,
            MessageToMailboxTable.MAILBOX_ID,
            MessageToMailboxTable.MESSAGE_UID,
            MessageToMailboxTable.MOD_SEQ,
            MessageToMailboxTable.THREAD_ID,
            MessageToMailboxTable.IS_DELETED,
            MessageToMailboxTable.IS_ANSWERED,
            MessageToMailboxTable.IS_DRAFT,
            MessageToMailboxTable.IS_FLAGGED,
            MessageToMailboxTable.IS_RECENT,
            MessageToMailboxTable.IS_SEEN,
            MessageToMailboxTable.USER_FLAGS,
            MessageToMailboxTable.SAVE_DATE};
    }

    class MetaData implements PostgresMailboxMessageFetchStrategy {
        public static final Field<?>[] FETCH_FIELDS = fetchFieldsMetadata();
        public static final Content EMPTY_CONTENT = BYTE_TO_CONTENT_FUNCTION.apply(new byte[0]);
        public static final PropertyBuilder EMPTY_PROPERTY_BUILDER = new PropertyBuilder();


        @Override
        public Field<?>[] fetchFields() {
            return FETCH_FIELDS;
        }

        @Override
        public Function<Record, SimpleMailboxMessage.Builder> toMessageBuilder() {
            return record -> toMessageBuilderMetadata()
                .apply(record)
                .content(EMPTY_CONTENT)
                .properties(EMPTY_PROPERTY_BUILDER);
        }
    }

    class Full implements PostgresMailboxMessageFetchStrategy {

        public static final Field<?>[] FETCH_FIELDS = ArrayUtils.addAll(fetchFieldsMetadata(),
            MessageTable.HEADER_CONTENT,
            MessageTable.TEXTUAL_LINE_COUNT,
            MessageTable.CONTENT_DESCRIPTION,
            MessageTable.CONTENT_LOCATION,
            MessageTable.CONTENT_TRANSFER_ENCODING,
            MessageTable.CONTENT_DISPOSITION_TYPE,
            MessageTable.CONTENT_ID,
            MessageTable.CONTENT_MD5,
            MessageTable.CONTENT_LANGUAGE,
            MessageTable.CONTENT_TYPE_PARAMETERS,
            MessageTable.CONTENT_DISPOSITION_PARAMETERS);

        @Override
        public Field<?>[] fetchFields() {
            return FETCH_FIELDS;
        }

        @Override
        public Function<Record, SimpleMailboxMessage.Builder> toMessageBuilder() {
            return record -> toMessageBuilderMetadata()
                .apply(record)
                .content(BYTE_TO_CONTENT_FUNCTION.apply(record.get(HEADER_CONTENT)))
                .properties(RECORD_TO_PROPERTIES_FUNCTION.apply(record));
        }
    }

}
