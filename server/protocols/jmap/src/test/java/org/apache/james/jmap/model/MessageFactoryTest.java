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
package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.commons.io.IOUtils;
import org.apache.james.jmap.model.MessageFactory.MetaDataWithContent;
import org.apache.james.jmap.utils.HtmlTextExtractor;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MessageAttachment;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MessageFactoryTest {
    private static final InMemoryId MAILBOX_ID = InMemoryId.of(18L);
    private static final ZoneId UTC_ZONE_ID = ZoneId.of("Z");
    private static final ZonedDateTime ZONED_DATE = ZonedDateTime.of(2015, 07, 14, 12, 30, 42, 0, UTC_ZONE_ID);
    private static final Date INTERNAL_DATE = Date.from(ZONED_DATE.toInstant());

    private MessageFactory messageFactory;
    private MessagePreviewGenerator messagePreview ;
    private HtmlTextExtractor htmlTextExtractor;
    
    @Before
    public void setUp() {
        htmlTextExtractor = mock(HtmlTextExtractor.class);
        messagePreview = new MessagePreviewGenerator(htmlTextExtractor);
        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();
        messageFactory = new MessageFactory(messagePreview, messageContentExtractor);
    }
    @Test
    public void emptyMailShouldBeLoadedIntoMessage() throws Exception {
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(2)
                .flags(new Flags(Flag.SEEN))
                .size(0)
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream("".getBytes(Charsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(MessageId.of("test|test|2"))
                .build();

        Message testee = messageFactory.fromMetaDataWithContent(testMail);
        assertThat(testee)
            .extracting(Message::getPreview, Message::getSize, Message::getSubject, Message::getHeaders, Message::getDate)
            .containsExactly("(Empty)", 0L, "", ImmutableMap.of("Date", "Tue, 14 Jul 2015 12:30:42 +0000", "MIME-Version", "1.0"), ZONED_DATE);
    }

    @Test
    public void flagsShouldBeSetIntoMessage() throws Exception {
        Flags flags = new Flags();
        flags.add(Flag.ANSWERED);
        flags.add(Flag.FLAGGED);
        flags.add(Flag.DRAFT);
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(2)
                .flags(flags)
                .size(0)
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream("".getBytes(Charsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(MessageId.of("test|test|2"))
                .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);
        assertThat(testee)
            .extracting(Message::isIsUnread, Message::isIsFlagged, Message::isIsAnswered, Message::isIsDraft)
            .containsExactly(true, true, true, true);
    }

    @Test
    public void headersShouldBeSetIntoMessage() throws Exception {
        String headers = "From: user <user@domain>\n"
                + "Subject: test subject\n"
                + "To: user1 <user1@domain>, user2 <user2@domain>\n"
                + "Cc: usercc <usercc@domain>\n"
                + "Bcc: userbcc <userbcc@domain>\n"
                + "Reply-To: \"user to reply to\" <user.reply.to@domain>\n"
                + "In-Reply-To: <SNT124-W2664003139C1E520CF4F6787D30@phx.gbl>\n"
                + "Other-header: other header value";
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(2)
                .flags(new Flags(Flag.SEEN))
                .size(headers.length())
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream(headers.getBytes(Charsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(MessageId.of("user|box|2"))
                .build();

        Emailer user = Emailer.builder().name("user").email("user@domain").build();
        Emailer user1 = Emailer.builder().name("user1").email("user1@domain").build();
        Emailer user2 = Emailer.builder().name("user2").email("user2@domain").build();
        Emailer usercc = Emailer.builder().name("usercc").email("usercc@domain").build();
        Emailer userbcc = Emailer.builder().name("userbcc").email("userbcc@domain").build();
        Emailer userRT = Emailer.builder().name("user to reply to").email("user.reply.to@domain").build();
        ImmutableMap<String, String> headersMap = ImmutableMap.<String, String>builder()
                .put("Cc", "usercc <usercc@domain>")
                .put("Bcc", "userbcc <userbcc@domain>")
                .put("Subject", "test subject")
                .put("From", "user <user@domain>")
                .put("To", "user1 <user1@domain>, user2 <user2@domain>")
                .put("Reply-To", "\"user to reply to\" <user.reply.to@domain>")
                .put("In-Reply-To", "<SNT124-W2664003139C1E520CF4F6787D30@phx.gbl>")
                .put("Other-header", "other header value")
                .put("Date", "Tue, 14 Jul 2015 12:30:42 +0000")
                .put("MIME-Version", "1.0")
                .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);
        Message expected = Message.builder()
                .id(MessageId.of("user|box|2"))
                .blobId(BlobId.of("2"))
                .threadId("user|box|2")
                .mailboxIds(ImmutableList.of(MAILBOX_ID))
                .inReplyToMessageId("<SNT124-W2664003139C1E520CF4F6787D30@phx.gbl>")
                .headers(headersMap)
                .from(user)
                .to(ImmutableList.of(user1, user2))
                .cc(ImmutableList.of(usercc))
                .bcc(ImmutableList.of(userbcc))
                .replyTo(ImmutableList.of(userRT))
                .subject("test subject")
                .date(ZONED_DATE)
                .size(headers.length())
                .preview("(Empty)")
                .textBody("")
                .build();
        assertThat(testee).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void textBodyShouldBeSetIntoMessage() throws Exception {
        String headers = "Subject: test subject\n";
        String body = "Mail body";
        String mail = headers + "\n" + body;
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(2)
                .flags(new Flags(Flag.SEEN))
                .size(mail.length())
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream(mail.getBytes(Charsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(MessageId.of("user|box|2"))
                .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);
        assertThat(testee.getTextBody()).hasValue("Mail body");
    }

    @Test
    public void previewShouldBeLimitedTo256Length() throws Exception {
        String headers = "Subject: test subject\n";
        String body300 = "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999";
        String expectedPreview = "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999" 
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999" 
                + "00000000001111111111222222222233333333334444444444555...";
        assertThat(body300.length()).isEqualTo(300);
        assertThat(expectedPreview.length()).isEqualTo(256);
        String mail = headers + "\n" + body300;
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(2)
                .flags(new Flags(Flag.SEEN))
                .size(mail.length())
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream(mail.getBytes(Charsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(MessageId.of("user|box|2"))
                .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);
        assertThat(testee.getPreview()).isEqualTo(expectedPreview);
    }
    
    @Test
    public void attachmentsShouldBeEmptyWhenNone() throws Exception {
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(2)
                .flags(new Flags(Flag.SEEN))
                .size(0)
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream(IOUtils.toByteArray(ClassLoader.getSystemResourceAsStream("spamMail.eml"))))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(MessageId.of("user|box|2"))
                .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);
        assertThat(testee.getAttachments()).isEmpty();
    }
    
    @Test
    public void attachmentsShouldBeRetrievedWhenSome() throws Exception {
        String payload = "payload";
        BlobId blodId = BlobId.of("id1");
        String type = "content";
        Attachment expectedAttachment = Attachment.builder()
                .blobId(blodId)
                .size(payload.length())
                .type(type)
                .cid("cid")
                .isInline(true)
                .build();
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(2)
                .flags(new Flags(Flag.SEEN))
                .size(0)
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream(IOUtils.toByteArray(ClassLoader.getSystemResourceAsStream("spamMail.eml"))))
                .attachments(ImmutableList.of(MessageAttachment.builder()
                        .attachment(org.apache.james.mailbox.model.Attachment.builder()
                                .attachmentId(AttachmentId.from(blodId.getRawValue()))
                                .bytes(payload.getBytes())
                                .type(type)
                                .build())
                        .cid(Cid.from("cid"))
                        .isInline(true)
                        .build()))
                .mailboxId(MAILBOX_ID)
                .messageId(MessageId.of("user|box|2"))
                .build();

        Message testee = messageFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getAttachments()).hasSize(1);
        assertThat(testee.getAttachments().get(0)).isEqualToComparingFieldByField(expectedAttachment);
    }
}
