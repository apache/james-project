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

package org.apache.james.jmap.json;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.apache.james.jmap.model.Emailer;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.SubMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public interface ParsingWritingObjects {

    public interface Common {
        MessageId MESSAGE_ID = MessageId.of("username|mailbox|1");
        String BLOB_ID = "myBlobId";
        String THREAD_ID = "myThreadId";
        ImmutableList<String> MAILBOX_IDS = ImmutableList.of("mailboxId1", "mailboxId2");
        String IN_REPLY_TO_MESSAGE_ID = "myInReplyToMessageId";
        boolean IS_UNREAD = true;
        boolean IS_FLAGGED = true;
        boolean IS_ANSWERED = true;
        boolean IS_DRAFT = true;
        boolean HAS_ATTACHMENT = true;
        ImmutableMap<String, String> HEADERS = ImmutableMap.of("h1", "h1Value", "h2", "h2Value");
        Emailer FROM = Emailer.builder().name("myName").email("myEmail@james.org").build();
        ImmutableList<Emailer> TO = ImmutableList.of(Emailer.builder().name("to1").email("to1@james.org").build(),
                Emailer.builder().name("to2").email("to2@james.org").build());
        ImmutableList<Emailer> CC = ImmutableList.of(Emailer.builder().name("cc1").email("cc1@james.org").build(),
                Emailer.builder().name("cc2").email("cc2@james.org").build());
        ImmutableList<Emailer> BCC = ImmutableList.of(Emailer.builder().name("bcc1").email("bcc1@james.org").build(),
                Emailer.builder().name("bcc2").email("bcc2@james.org").build());
        ImmutableList<Emailer> REPLY_TO = ImmutableList.of(Emailer.builder().name("replyTo1").email("replyTo1@james.org").build(),
                Emailer.builder().name("replyTo2").email("replyTo2@james.org").build());
        String SUBJECT = "mySubject";
        ZonedDateTime DATE = ZonedDateTime.parse("2014-10-30T14:12:00Z").withZoneSameLocal(ZoneId.of("GMT"));
        int SIZE = 1024;
        String PREVIEW = "myPreview";
        String TEXT_BODY = "myTextBody";
        String HTML_BODY = "<h1>myHtmlBody</h1>";
    }

    Message MESSAGE = Message.builder()
            .id(Common.MESSAGE_ID)
            .blobId(Common.BLOB_ID)
            .threadId(Common.THREAD_ID)
            .mailboxIds(Common.MAILBOX_IDS)
            .inReplyToMessageId(Common.IN_REPLY_TO_MESSAGE_ID)
            .isUnread(Common.IS_UNREAD)
            .isFlagged(Common.IS_FLAGGED)
            .isAnswered(Common.IS_ANSWERED)
            .isDraft(Common.IS_DRAFT)
            .hasAttachment(Common.HAS_ATTACHMENT)
            .headers(Common.HEADERS)
            .from(Common.FROM)
            .to(Common.TO)
            .cc(Common.CC)
            .bcc(Common.BCC)
            .replyTo(Common.REPLY_TO)
            .subject(Common.SUBJECT)
            .date(Common.DATE)
            .size(Common.SIZE)
            .preview(Common.PREVIEW)
            .textBody(Common.TEXT_BODY)
            .htmlBody(Common.HTML_BODY)
            .build();

    SubMessage SUB_MESSAGE = SubMessage.builder()
            .headers(Common.HEADERS)
            .from(Common.FROM)
            .to(Common.TO)
            .cc(Common.CC)
            .bcc(Common.BCC)
            .replyTo(Common.REPLY_TO)
            .subject(Common.SUBJECT)
            .date(Common.DATE)
            .textBody(Common.TEXT_BODY)
            .htmlBody(Common.HTML_BODY)
            .build();
}
