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

package org.apache.james.mailbox.lucene.search;

import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.ATTACHMENT_FILE_NAME_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.ATTACHMENT_TEXT_CONTENT_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.BASE_SUBJECT_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.BCC_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.BODY_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.CC_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.FIRST_CC_MAILBOX_NAME_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.FIRST_FROM_MAILBOX_NAME_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.FIRST_TO_MAILBOX_NAME_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.FLAGS_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.FROM_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.HAS_ATTACHMENT_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.HEADERS_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.ID_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.INTERNAL_DATE_FIELD_DAY_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.INTERNAL_DATE_FIELD_HOUR_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.INTERNAL_DATE_FIELD_MILLISECOND_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.INTERNAL_DATE_FIELD_MINUTE_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.INTERNAL_DATE_FIELD_MONTH_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.INTERNAL_DATE_FIELD_SECOND_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.INTERNAL_DATE_FIELD_YEAR_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.MAILBOX_ID_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.MESSAGE_ID_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.PREFIX_HEADER_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SAVE_DATE_FIELD_DAY_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SAVE_DATE_FIELD_HOUR_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SAVE_DATE_FIELD_MINUTE_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SAVE_DATE_FIELD_MONTH_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SAVE_DATE_FIELD_SECOND_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SAVE_DATE_FIELD_YEAR_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SENT_DATE_FIELD_DAY_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SENT_DATE_FIELD_HOUR_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SENT_DATE_FIELD_MILLISECOND_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SENT_DATE_FIELD_MINUTE_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SENT_DATE_FIELD_MONTH_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SENT_DATE_FIELD_SECOND_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SENT_DATE_FIELD_YEAR_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SENT_DATE_SORT_FIELD_MILLISECOND_RESOLUTION;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SIZE_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SUBJECT_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.THREAD_ID_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.TO_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.UID_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.USERS;

import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.SearchUtil;
import org.apache.james.mailbox.store.search.mime.EMailers;
import org.apache.james.mailbox.store.search.mime.HeaderCollection;
import org.apache.james.mailbox.store.search.mime.MimePart;
import org.apache.james.mailbox.store.search.mime.MimePartParser;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.util.BytesRef;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;

public class LuceneIndexableDocument {
    public static final Map<Flags.Flag, String> SYSTEM_FLAG_STRING_MAP = ImmutableMap.<Flags.Flag, String>builder()
        .put(Flags.Flag.ANSWERED, "\\ANSWERED")
        .put(Flags.Flag.DELETED, "\\DELETED")
        .put(Flags.Flag.DRAFT, "\\DRAFT")
        .put(Flags.Flag.FLAGGED, "\\FLAGGED")
        .put(Flags.Flag.RECENT, "\\RECENT")
        .put(Flags.Flag.SEEN, "\\FLAG")
        .build();

    private final TextExtractor textExtractor;

    public LuceneIndexableDocument(TextExtractor textExtractor) {
        this.textExtractor = textExtractor;
    }

    public Mono<Document> createMessageDocument(MailboxMessage message, MailboxSession session) {
        return Throwing.supplier(() -> new MimePartParser(textExtractor).parse(message.getFullContent())).get()
            .asMimePart(textExtractor)
            .map(parsingResult -> createMessageDocument(message, session, parsingResult));
    }

    /**
     * Create a new {@link Document} for the given {@link MailboxMessage}. This Document does not contain any flags data. The {@link Flags} are stored in a seperate Document.
     * <p>
     * See {@link #createFlagsDocument(MailboxMessage)}
     */
    public Document createMessageDocument(MailboxMessage message, MailboxSession session, MimePart mimePartExtracted) {
        Document doc = new Document();
        doc.add(new StringField(USERS, session.getUser().asString().toUpperCase(Locale.US), Field.Store.YES));
        doc.add(new StringField(MAILBOX_ID_FIELD, message.getMailboxId().serialize().toUpperCase(Locale.US), Field.Store.YES));
        doc.add(new NumericDocValuesField(UID_FIELD, message.getUid().asLong()));
        doc.add(new LongPoint(UID_FIELD, message.getUid().asLong()));
        doc.add(new StoredField(UID_FIELD, message.getUid().asLong()));
        doc.add(new StringField(HAS_ATTACHMENT_FIELD, Boolean.toString(MessageAttachmentMetadata.hasNonInlinedAttachment(message.getAttachments())), Field.Store.YES));
        doc.add(new LongPoint(SIZE_FIELD, message.getFullContentOctets()));
        doc.add(new NumericDocValuesField(SIZE_FIELD, message.getFullContentOctets()));

        // create a unique key for the document which can be used later on updates to find the document
        doc.add(new StringField(ID_FIELD, message.getMailboxId().serialize().toUpperCase(Locale.US) + "-" + message.getUid().asLong(), Field.Store.YES));

        Optional.ofNullable(SearchUtil.getSerializedMessageIdIfSupportedByUnderlyingStorageOrNull(message))
            .ifPresent(serializedMessageId -> doc.add(new StringField(MESSAGE_ID_FIELD, serializedMessageId, Field.Store.YES)));
        Optional.ofNullable(SearchUtil.getSerializedThreadIdIfSupportedByUnderlyingStorageOrNull(message))
            .ifPresent(serializedThreadId -> {
                doc.add(new StringField(THREAD_ID_FIELD, serializedThreadId, Field.Store.YES));
                doc.add(new SortedDocValuesField(THREAD_ID_FIELD, new BytesRef(serializedThreadId)));
            });

        HeaderCollection headerCollection = mimePartExtracted.getHeaderCollection();

        // index date fields
        indexInternalDateFields(message.getInternalDate(), doc);
        message.getSaveDate().ifPresent(saveDate -> indexSaveDateFields(saveDate, doc));
        headerCollection.getSentDate()
            .map(zonedDateTime -> Date.from(zonedDateTime.toInstant()))
            .or(() -> Optional.ofNullable(message.getInternalDate()))
            .ifPresent(sentDate -> indexSentDateFields(sentDate, doc));

        // index header
        headerCollection.getHeaders()
            .forEach(header -> {
                String headerName = uppercase(header.getHeaderName());
                String headerValue = uppercase(header.getValue());
                doc.add(new TextField(HEADERS_FIELD, String.format("%s: %s", headerName, headerValue), Field.Store.NO));
                doc.add(new TextField(PREFIX_HEADER_FIELD + headerName, headerValue, Field.Store.NO));

                switch (headerName) {
                    case "TO":
                        doc.add(new TextField(TO_FIELD, headerValue, Field.Store.NO));
                        doc.add(new SortedSetDocValuesField(FIRST_TO_MAILBOX_NAME_FIELD, new BytesRef(SearchUtil.getMailboxAddress(header.getValue()))));
                        break;
                    case "FROM":
                        doc.add(new TextField(FROM_FIELD, headerValue, Field.Store.NO));
                        doc.add(new SortedSetDocValuesField(FIRST_FROM_MAILBOX_NAME_FIELD, new BytesRef(SearchUtil.getMailboxAddress(header.getValue()))));
                        break;
                    case "CC":
                        doc.add(new TextField(CC_FIELD, headerValue, Field.Store.NO));
                        doc.add(new SortedSetDocValuesField(FIRST_CC_MAILBOX_NAME_FIELD, new BytesRef(SearchUtil.getMailboxAddress(header.getValue()))));
                        break;
                    case "BCC":
                        doc.add(new TextField(BCC_FIELD, headerValue, Field.Store.NO));
                        break;
                    case "SUBJECT":
                        doc.add(new StringField(SUBJECT_FIELD, header.getValue(), Field.Store.YES));
                        doc.add(new StringField(BASE_SUBJECT_FIELD, headerValue, Field.Store.NO));
                        doc.add(new SortedSetDocValuesField(BASE_SUBJECT_FIELD, new BytesRef(SearchUtil.getBaseSubject(headerValue))));
                        break;
                    default:
                        break;
                }
            });

        doc.add(new TextField(FROM_FIELD, uppercase(EMailers.from(headerCollection.getFromAddressSet()).serialize()), Field.Store.YES));
        doc.add(new TextField(TO_FIELD, uppercase(EMailers.from(headerCollection.getToAddressSet()).serialize()), Field.Store.YES));
        doc.add(new TextField(CC_FIELD, uppercase(EMailers.from(headerCollection.getCcAddressSet()).serialize()), Field.Store.YES));
        doc.add(new TextField(BCC_FIELD, uppercase(EMailers.from(headerCollection.getBccAddressSet()).serialize()), Field.Store.YES));

        // index body
        Optional<String> bodyText = mimePartExtracted.locateFirstTextBody().map(SearchUtil::removeGreaterThanCharactersAtBeginningOfLine);
        Optional<String> bodyHtml = mimePartExtracted.locateFirstHtmlBody();

        bodyText.or(() -> bodyHtml)
            .ifPresent(bodyContent -> doc.add(new TextField(BODY_FIELD, bodyContent, Field.Store.YES)));

        // index attachment
        mimePartExtracted.getAttachmentsStream().forEach(attachmentFields -> {
            attachmentFields.getTextualBody().ifPresent(textualBody -> doc.add(new TextField(ATTACHMENT_TEXT_CONTENT_FIELD, textualBody, Field.Store.YES)));
            attachmentFields.getFileName().ifPresent(fileName -> doc.add(new StringField(ATTACHMENT_FILE_NAME_FIELD, uppercase(fileName), Field.Store.YES)));
        });
        return doc;
    }

    /**
     * Index the {@link Flags} and add it to the {@link Document}
     */
    public Document createFlagsDocument(MailboxMessage message) {
        return createFlagsDocument(message.getMailboxId(), message.getUid(), message.createFlags());
    }

    public Document createFlagsDocument(MailboxId mailboxId, final MessageUid messageUid, Flags messageFlags) {
        Document doc = new Document();
        doc.add(new StringField(ID_FIELD, createFlagsIdField(mailboxId, messageUid), Field.Store.YES));
        doc.add(new StringField(MAILBOX_ID_FIELD, mailboxId.serialize(), Field.Store.YES));

        Optional.of(messageUid.asLong())
            .ifPresent(uidAsLong -> {
                doc.add(new NumericDocValuesField(UID_FIELD, uidAsLong));
                doc.add(new LongPoint(UID_FIELD, uidAsLong));
                doc.add(new StoredField(UID_FIELD, uidAsLong));
            });

        Arrays.stream(messageFlags.getSystemFlags())
            .forEach(sysFlag ->
                doc.add(new StringField(FLAGS_FIELD, Optional.ofNullable(SYSTEM_FLAG_STRING_MAP.get(sysFlag))
                    .orElse(sysFlag.toString()), Field.Store.YES)));

        Arrays.stream(messageFlags.getUserFlags())
            .forEach(userFlag -> doc.add(new StringField(FLAGS_FIELD, lowercase(userFlag), Field.Store.YES)));

        // if no flags are there we just use a empty field
        if (messageFlags.getSystemFlags().length == 0 && messageFlags.getUserFlags().length == 0) {
            doc.add(new StringField(FLAGS_FIELD, "", Field.Store.NO));
        }

        return doc;
    }

    public static String createFlagsIdField(MailboxId mailboxId, MessageUid messageUid) {
        return "flags-" + mailboxId.serialize() + "-" + messageUid.asLong();
    }

    private static void indexSaveDateFields(Date saveDate, Document doc) {
        doc.add(new StringField(SAVE_DATE_FIELD_YEAR_RESOLUTION, DateTools.dateToString(saveDate, DateTools.Resolution.YEAR), Field.Store.NO));
        doc.add(new StringField(SAVE_DATE_FIELD_MONTH_RESOLUTION, DateTools.dateToString(saveDate, DateTools.Resolution.MONTH), Field.Store.NO));
        doc.add(new StringField(SAVE_DATE_FIELD_DAY_RESOLUTION, DateTools.dateToString(saveDate, DateTools.Resolution.DAY), Field.Store.NO));
        doc.add(new StringField(SAVE_DATE_FIELD_HOUR_RESOLUTION, DateTools.dateToString(saveDate, DateTools.Resolution.HOUR), Field.Store.NO));
        doc.add(new StringField(SAVE_DATE_FIELD_MINUTE_RESOLUTION, DateTools.dateToString(saveDate, DateTools.Resolution.MINUTE), Field.Store.NO));
        doc.add(new StringField(SAVE_DATE_FIELD_SECOND_RESOLUTION, DateTools.dateToString(saveDate, DateTools.Resolution.SECOND), Field.Store.NO));
    }

    private static void indexInternalDateFields(Date date, Document doc) {
        doc.add(new StringField(INTERNAL_DATE_FIELD_YEAR_RESOLUTION, DateTools.dateToString(date, DateTools.Resolution.YEAR), Field.Store.NO));
        doc.add(new StringField(INTERNAL_DATE_FIELD_MONTH_RESOLUTION, DateTools.dateToString(date, DateTools.Resolution.MONTH), Field.Store.NO));
        doc.add(new StringField(INTERNAL_DATE_FIELD_DAY_RESOLUTION, DateTools.dateToString(date, DateTools.Resolution.DAY), Field.Store.NO));
        doc.add(new StringField(INTERNAL_DATE_FIELD_HOUR_RESOLUTION, DateTools.dateToString(date, DateTools.Resolution.HOUR), Field.Store.NO));
        doc.add(new StringField(INTERNAL_DATE_FIELD_MINUTE_RESOLUTION, DateTools.dateToString(date, DateTools.Resolution.MINUTE), Field.Store.NO));
        doc.add(new StringField(INTERNAL_DATE_FIELD_SECOND_RESOLUTION, DateTools.dateToString(date, DateTools.Resolution.SECOND), Field.Store.NO));
        doc.add(new NumericDocValuesField(INTERNAL_DATE_FIELD_MILLISECOND_RESOLUTION, Long.parseLong(DateTools.dateToString(date, DateTools.Resolution.MILLISECOND))));
    }

    private static void indexSentDateFields(Date sentDate, Document doc) {
        doc.add(new StringField(SENT_DATE_FIELD_YEAR_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.YEAR), Field.Store.NO));
        doc.add(new StringField(SENT_DATE_FIELD_MONTH_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.MONTH), Field.Store.NO));
        doc.add(new StringField(SENT_DATE_FIELD_DAY_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.DAY), Field.Store.NO));
        doc.add(new StringField(SENT_DATE_FIELD_HOUR_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.HOUR), Field.Store.NO));
        doc.add(new StringField(SENT_DATE_FIELD_MINUTE_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.MINUTE), Field.Store.NO));
        doc.add(new StringField(SENT_DATE_FIELD_SECOND_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.SECOND), Field.Store.NO));
        doc.add(new StringField(SENT_DATE_FIELD_MILLISECOND_RESOLUTION, DateTools.dateToString(sentDate, DateTools.Resolution.MILLISECOND), Field.Store.NO));
        doc.add(new NumericDocValuesField(SENT_DATE_SORT_FIELD_MILLISECOND_RESOLUTION, Long.parseLong(DateTools.dateToString(sentDate, DateTools.Resolution.MILLISECOND))));
    }

    public static String uppercase(String value) {
        return value.toUpperCase(Locale.US);
    }

    public static String lowercase(String value) {
        return value.toLowerCase(Locale.US);
    }

}
