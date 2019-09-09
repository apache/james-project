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

package org.apache.james.vault.search;

import static org.apache.james.vault.search.DeletedMessageField.ValueExtractor.DELETION_DATE_EXTRACTOR;
import static org.apache.james.vault.search.DeletedMessageField.ValueExtractor.DELIVERY_DATE_EXTRACTOR;
import static org.apache.james.vault.search.DeletedMessageField.ValueExtractor.HAS_ATTACHMENT_EXTRACTOR;
import static org.apache.james.vault.search.DeletedMessageField.ValueExtractor.ORIGIN_MAILBOXES_EXTRACTOR;
import static org.apache.james.vault.search.DeletedMessageField.ValueExtractor.RECIPIENTS_EXTRACTOR;
import static org.apache.james.vault.search.DeletedMessageField.ValueExtractor.SENDER_EXTRACTOR;
import static org.apache.james.vault.search.DeletedMessageField.ValueExtractor.SUBJECT_EXTRACTOR;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.vault.DeletedMessage;

public class DeletedMessageField<T> {

    private FieldName fieldName;
    private ValueExtractor<T> valueExtractor;

    private DeletedMessageField(FieldName fieldName, ValueExtractor<T> valueExtractor) {
        this.fieldName = fieldName;
        this.valueExtractor = valueExtractor;
    }

    public FieldName fieldName() {
        return fieldName;
    }

    ValueExtractor<T> valueExtractor() {
        return valueExtractor;
    }

    interface ValueExtractor<T> {
        Optional<T> extract(DeletedMessage deletedMessage);

        ValueExtractor<ZonedDateTime> DELETION_DATE_EXTRACTOR = deletedMessage -> Optional.of(deletedMessage.getDeletionDate());
        ValueExtractor<ZonedDateTime> DELIVERY_DATE_EXTRACTOR = deletedMessage -> Optional.of(deletedMessage.getDeliveryDate());
        ValueExtractor<Collection<MailAddress>> RECIPIENTS_EXTRACTOR = deletedMessage -> Optional.of(deletedMessage.getRecipients());
        ValueExtractor<MailAddress> SENDER_EXTRACTOR = deletedMessage -> deletedMessage.getSender().asOptional();
        ValueExtractor<Boolean> HAS_ATTACHMENT_EXTRACTOR = deletedMessage -> Optional.of(deletedMessage.hasAttachment());
        ValueExtractor<Collection<MailboxId>> ORIGIN_MAILBOXES_EXTRACTOR = deletedMessage -> Optional.of(deletedMessage.getOriginMailboxes());
        ValueExtractor<String> SUBJECT_EXTRACTOR = DeletedMessage::getSubject;
    }

    static final DeletedMessageField<ZonedDateTime> DELETION_DATE = new DeletedMessageField<>(FieldName.DELETION_DATE, DELETION_DATE_EXTRACTOR);
    static final DeletedMessageField<ZonedDateTime> DELIVERY_DATE = new DeletedMessageField<>(FieldName.DELIVERY_DATE, DELIVERY_DATE_EXTRACTOR);
    static final DeletedMessageField<Collection<MailAddress>> RECIPIENTS = new DeletedMessageField<>(FieldName.RECIPIENTS, RECIPIENTS_EXTRACTOR);
    static final DeletedMessageField<MailAddress> SENDER = new DeletedMessageField<>(FieldName.SENDER, SENDER_EXTRACTOR);
    static final DeletedMessageField<Boolean> HAS_ATTACHMENT = new DeletedMessageField<>(FieldName.HAS_ATTACHMENT, HAS_ATTACHMENT_EXTRACTOR);
    static final DeletedMessageField<Collection<MailboxId>> ORIGIN_MAILBOXES = new DeletedMessageField<>(FieldName.ORIGIN_MAILBOXES, ORIGIN_MAILBOXES_EXTRACTOR);
    static final DeletedMessageField<String> SUBJECT = new DeletedMessageField<>(FieldName.SUBJECT, SUBJECT_EXTRACTOR);

}
