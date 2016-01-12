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

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SubMailboxMessageTest {
    @Test(expected=IllegalStateException.class)
    public void buildShouldThrowWhenHeadersIsNull() {
        SubMessage.builder().build();
    }

    @Test(expected=IllegalStateException.class)
    public void buildShouldThrowWhenSubjectIsNull() {
        SubMessage.builder().headers(ImmutableMap.of()).build();
    }

    @Test(expected=IllegalStateException.class)
    public void buildShouldThrowWhenSubjectIsEmpty() {
        SubMessage.builder().headers(ImmutableMap.of()).subject("").build();
    }

    @Test(expected=IllegalStateException.class)
    public void buildShouldThrowWhenDateIsNull() {
        SubMessage.builder().headers(ImmutableMap.of()).subject("subject").build();
    }

    @Test
    public void buildShouldWorkWhenMandatoryFieldsArePresent() {
        ZonedDateTime currentDate = ZonedDateTime.now();
        SubMessage expected = new SubMessage(ImmutableMap.of("key", "value"), Optional.empty(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(),
                "subject", currentDate, Optional.empty(), Optional.empty(), ImmutableList.of(), ImmutableMap.of());
        SubMessage tested = SubMessage.builder()
            .headers(ImmutableMap.of("key", "value"))
            .subject("subject")
            .date(currentDate)
            .build();
        assertThat(tested).isEqualToComparingFieldByField(expected);
    }

    @Test(expected=IllegalStateException.class)
    public void buildShouldThrowWhenAttachedMessageIsNotMatchingAttachments() {
        Attachment simpleAttachment = Attachment.builder().blobId("blobId").type("type").name("name").size(123).build();
        ImmutableList<Attachment> attachments = ImmutableList.of(simpleAttachment);
        SubMessage simpleMessage = SubMessage.builder()
                .headers(ImmutableMap.of("key", "value"))
                .subject("subject")
                .date(ZonedDateTime.now())
                .build();
        ImmutableMap<String, SubMessage> attachedMessages = ImmutableMap.of("differentBlobId", simpleMessage);
        SubMessage.builder()
            .headers(ImmutableMap.of("key", "value"))
            .subject("subject")
            .date(ZonedDateTime.now())
            .attachments(attachments)
            .attachedMessages(attachedMessages)
            .build();
    }

    @Test
    public void buildShouldWorkWhenAllFieldsArePresent() {
        Emailer from = Emailer.builder().name("from").email("from@domain").build();
        ImmutableList<Emailer> to = ImmutableList.of(Emailer.builder().name("to").email("to@domain").build());
        ImmutableList<Emailer> cc = ImmutableList.of(Emailer.builder().name("cc").email("cc@domain").build());
        ImmutableList<Emailer> bcc = ImmutableList.of(Emailer.builder().name("bcc").email("bcc@domain").build());
        ImmutableList<Emailer> replyTo = ImmutableList.of(Emailer.builder().name("replyTo").email("replyTo@domain").build());
        ZonedDateTime currentDate = ZonedDateTime.now();
        Attachment simpleAttachment = Attachment.builder().blobId("blobId").type("type").name("name").size(123).build();
        ImmutableList<Attachment> attachments = ImmutableList.of(simpleAttachment);
        SubMessage simpleMessage = SubMessage.builder()
                .headers(ImmutableMap.of("key", "value"))
                .subject("subject")
                .date(currentDate)
                .build();
        ImmutableMap<String, SubMessage> attachedMessages = ImmutableMap.of("blobId", simpleMessage);
        SubMessage expected = new SubMessage(
                ImmutableMap.of("key", "value"),
                Optional.of(from),
                to,
                cc,
                bcc,
                replyTo,
                "subject",
                currentDate,
                Optional.of("textBody"),
                Optional.of("htmlBody"),
                attachments,
                attachedMessages);
        SubMessage tested = SubMessage.builder()
            .headers(ImmutableMap.of("key", "value"))
            .from(from)
            .to(to)
            .cc(cc)
            .bcc(bcc)
            .replyTo(replyTo)
            .subject("subject")
            .date(currentDate)
            .textBody("textBody")
            .htmlBody("htmlBody")
            .attachments(attachments)
            .attachedMessages(attachedMessages)
            .build();
        assertThat(tested).isEqualToComparingFieldByField(expected);
    }
}
