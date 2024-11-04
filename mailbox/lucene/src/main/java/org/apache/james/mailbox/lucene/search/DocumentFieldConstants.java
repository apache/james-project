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

import java.util.function.Function;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

public interface DocumentFieldConstants {

    /**
     * {@link Field} which will contain the unique index of the {@link Document}
     */
    String ID_FIELD = "id";


    /**
     * {@link Field} which will contain uid of the {@link MailboxMessage}
     */
    String UID_FIELD = "uid";

    /**
     * {@link Field} boolean field that say if the message as an attachment or not
     */
    String HAS_ATTACHMENT_FIELD = "hasAttachment";

    /**
     * {@link Field} which will contain the {@link Flags} of the {@link MailboxMessage}
     */
    String FLAGS_FIELD = "flags";

    /**
     * {@link Field} which will contain the size of the {@link MailboxMessage}
     */
    String SIZE_FIELD = "size";

    /**
     * {@link Field} which will contain the body of the {@link MailboxMessage}
     */
    String BODY_FIELD = "body";

    /**
     * Prefix which will be used for each message header to store it also in a seperate {@link Field}
     */
    String PREFIX_HEADER_FIELD = "header_";

    /**
     * {@link Field} which will contain the whole message header of the {@link MailboxMessage}
     */
    String HEADERS_FIELD = "headers";

    /**
     * {@link Field} which will contain the mod-sequence of the message
     */
    String MODSEQ_FIELD = "modSeq";

    /**
     * {@link Field} which will contain the threadId of the message
     */
    String THREAD_ID_FIELD = "threadId";

    /**
     * {@link Field} which will contain the TO-Address of the message
     */
    String TO_FIELD = "to";

    String FIRST_TO_MAILBOX_NAME_FIELD = "firstToMailboxName";

    /**
     * {@link Field} which will contain the CC-Address of the message
     */
    String CC_FIELD = "cc";

    String FIRST_CC_MAILBOX_NAME_FIELD = "firstCcMailboxName";


    /**
     * {@link Field} which will contain the FROM-Address of the message
     */
    String FROM_FIELD = "from";

    String FIRST_FROM_MAILBOX_NAME_FIELD = "firstFromMailboxName";

    /**
     * {@link Field} which will contain the BCC-Address of the message
     */
    String BCC_FIELD = "bcc";


    String BASE_SUBJECT_FIELD = "baseSubject";
    String SUBJECT_FIELD = "subject";

    /**
     * {@link Field} which contain the internalDate of the message with YEAR-Resolution
     */
    String INTERNAL_DATE_FIELD_YEAR_RESOLUTION = "internaldateYearResolution";


    /**
     * {@link Field} which contain the internalDate of the message with MONTH-Resolution
     */
    String INTERNAL_DATE_FIELD_MONTH_RESOLUTION = "internaldateMonthResolution";

    /**
     * {@link Field} which contain the internalDate of the message with DAY-Resolution
     */
    String INTERNAL_DATE_FIELD_DAY_RESOLUTION = "internaldateDayResolution";

    /**
     * {@link Field} which contain the internalDate of the message with HOUR-Resolution
     */
    String INTERNAL_DATE_FIELD_HOUR_RESOLUTION = "internaldateHourResolution";

    /**
     * {@link Field} which contain the internalDate of the message with MINUTE-Resolution
     */
    String INTERNAL_DATE_FIELD_MINUTE_RESOLUTION = "internaldateMinuteResolution";

    /**
     * {@link Field} which contain the internalDate of the message with SECOND-Resolution
     */
    String INTERNAL_DATE_FIELD_SECOND_RESOLUTION = "internaldateSecondResolution";


    /**
     * {@link Field} which contain the internalDate of the message with MILLISECOND-Resolution
     */
    String INTERNAL_DATE_FIELD_MILLISECOND_RESOLUTION = "internaldateMillisecondResolution";

    /**
     * {@link Field} which contain the saveDate of the message with YEAR-Resolution
     */
    String SAVE_DATE_FIELD_YEAR_RESOLUTION = "saveDateYearResolution";

    /**
     * {@link Field} which contain the saveDate of the message with MONTH-Resolution
     */
    String SAVE_DATE_FIELD_MONTH_RESOLUTION = "saveDateMonthResolution";

    /**
     * {@link Field} which contain the saveDate of the message with DAY-Resolution
     */
    String SAVE_DATE_FIELD_DAY_RESOLUTION = "saveDateDayResolution";

    /**
     * {@link Field} which contain the saveDate of the message with HOUR-Resolution
     */
    String SAVE_DATE_FIELD_HOUR_RESOLUTION = "saveDateHourResolution";

    /**
     * {@link Field} which contain the saveDate of the message with MINUTE-Resolution
     */
    String SAVE_DATE_FIELD_MINUTE_RESOLUTION = "saveDateMinuteResolution";

    /**
     * {@link Field} which contain the saveDate of the message with SECOND-Resolution
     */
    String SAVE_DATE_FIELD_SECOND_RESOLUTION = "saveDateSecondResolution";

    /**
     * {@link Field} which will contain the id of the {@link Mailbox}
     */
    String MAILBOX_ID_FIELD = "mailboxid";

    /**
     * {@link Field} which will contain the user of the {@link MailboxSession}
     */
    String USERS = "userSession";
    /**
     * {@link Field} which will contain the id of the {@link MessageId}
     */
    String MESSAGE_ID_FIELD = "messageid";

    /**
     * {@link Field} which contain the Date header of the message with YEAR-Resolution
     */
    String SENT_DATE_FIELD_YEAR_RESOLUTION = "sentdateYearResolution";


    /**
     * {@link Field} which contain the Date header of the message with MONTH-Resolution
     */
    String SENT_DATE_FIELD_MONTH_RESOLUTION = "sentdateMonthResolution";

    /**
     * {@link Field} which contain the Date header of the message with DAY-Resolution
     */
    String SENT_DATE_FIELD_DAY_RESOLUTION = "sentdateDayResolution";

    /**
     * {@link Field} which contain the Date header of the message with HOUR-Resolution
     */
    String SENT_DATE_FIELD_HOUR_RESOLUTION = "sentdateHourResolution";

    /**
     * {@link Field} which contain the Date header of the message with MINUTE-Resolution
     */
    String SENT_DATE_FIELD_MINUTE_RESOLUTION = "sentdateMinuteResolution";

    /**
     * {@link Field} which contain the Date header of the message with SECOND-Resolution
     */
    String SENT_DATE_FIELD_SECOND_RESOLUTION = "sentdateSecondResolution";


    /**
     * {@link Field} which contain the Date header of the message with MILLISECOND-Resolution
     */
    String SENT_DATE_FIELD_MILLISECOND_RESOLUTION = "sentdateMillisecondResolution";

    String SENT_DATE_SORT_FIELD_MILLISECOND_RESOLUTION = "sentdateSort";

    String ATTACHMENTS = "attachments";

    interface Attachment {
        String TEXT_CONTENT = "textContent";
        String MEDIA_TYPE = "mediaType";
        String SUBTYPE = "subtype";
        String CONTENT_DISPOSITION = "contentDisposition";
        String FILENAME = "fileName";
        String FILE_EXTENSION = "fileExtension";
    }

    Function<String, String> ATTACHMENT_FIELD_FUNCTION = (fieldName) -> ATTACHMENTS + "." + fieldName;

    String ATTACHMENT_TEXT_CONTENT_FIELD = ATTACHMENT_FIELD_FUNCTION.apply(Attachment.TEXT_CONTENT);
    String ATTACHMENT_FILE_NAME_FIELD = ATTACHMENT_FIELD_FUNCTION.apply(Attachment.FILENAME);

}
