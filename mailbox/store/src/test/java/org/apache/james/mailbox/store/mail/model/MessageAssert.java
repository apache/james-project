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

package org.apache.james.mailbox.store.mail.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.mail.Flags;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.assertj.core.api.AbstractAssert;

import com.google.common.base.Objects;

public class MessageAssert extends AbstractAssert<MessageAssert, MailboxMessage> {

    public MessageAssert(MailboxMessage actual) {
        super(actual, MessageAssert.class);
    }

    public static MessageAssert assertThat(MailboxMessage actual) {
        return new MessageAssert(actual);
    }

    public MessageAssert isEqualTo(MailboxMessage expected, MessageMapper.FetchType usedFetchType) throws IOException {
        isNotNull();
        if (!Objects.equal(actual.getUid(), expected.getUid())) {
            failWithMessage("Expected UID to be <%s> but was <%s>", expected.getUid(), actual.getUid());
        }
        return isEqualToWithoutUid(expected, usedFetchType);
    }

    public MessageAssert isEqualToWithoutAttachment(MailboxMessage expected, MessageMapper.FetchType usedFetchType) throws IOException {
        isNotNull();
        if (!Objects.equal(actual.getUid(), expected.getUid())) {
            failWithMessage("Expected UID to be <%s> but was <%s>", expected.getUid(), actual.getUid());
        }
        return isEqualToWithoutUidAndAttachment(expected, usedFetchType);
    }

    public MessageAssert isEqualToWithoutUid(MailboxMessage expected, MessageMapper.FetchType usedFetchType) throws IOException {
        isEqualToWithoutUidAndAttachment(expected, usedFetchType);
        if (usedFetchType == MessageMapper.FetchType.FULL) {
            if (!Objects.equal(actual.getAttachments(), expected.getAttachments())) {
                failWithMessage("Expected attachments to be <%s> but was <%s>", expected.getAttachments(), actual.getAttachments());
            }
        }
        return this;
    }

    public MessageAssert isEqualToWithoutUidAndAttachment(MailboxMessage expected, FetchType usedFetchType)  throws IOException {
        isNotNull();
        if (!Objects.equal(actual.getMailboxId(), expected.getMailboxId())) {
            failWithMessage("Expected Mailbox ID to be <%s> but was <%s>", expected.getMailboxId().toString(), actual.getMailboxId().toString());
        }
        if (!Objects.equal(actual.getInternalDate(), expected.getInternalDate())) {
            failWithMessage("Expected Internal Date to be <%s> but was <%s>", expected.getInternalDate(), actual.getInternalDate());
        }
        if (!Objects.equal(actual.getBodyOctets(), expected.getBodyOctets())) {
            failWithMessage("Expected Body octet to be <%s> but was <%s>", expected.getBodyOctets(), actual.getBodyOctets());
        }
        if (!Objects.equal(actual.getMediaType(), expected.getMediaType())) {
            failWithMessage("Expected Media type to be <%s> but was <%s>", expected.getBodyOctets(), actual.getBodyOctets());
        }
        if (!Objects.equal(actual.getSubType(), expected.getSubType())) {
            failWithMessage("Expected Sub type to be <%s> but was <%s>", expected.getBodyOctets(), actual.getBodyOctets());
        }
        if (usedFetchType == MessageMapper.FetchType.FULL) {
            if (!Objects.equal(actual.getFullContentOctets(), expected.getFullContentOctets())) {
                failWithMessage("Expected MailboxMessage size to be <%s> but was <%s>", expected.getFullContentOctets(), actual.getFullContentOctets());
            }
            if (!Objects.equal(IOUtils.toString(actual.getFullContent(), StandardCharsets.UTF_8), IOUtils.toString(expected.getFullContent(), StandardCharsets.UTF_8))) {
                failWithMessage("Expected Full content to be <%s> but was <%s>", IOUtils.toString(expected.getFullContent(), StandardCharsets.UTF_8), IOUtils.toString(actual.getFullContent(), StandardCharsets.UTF_8));
            }
        }
        if (usedFetchType == MessageMapper.FetchType.FULL || usedFetchType == MessageMapper.FetchType.HEADERS) {
            if (!Objects.equal(IOUtils.toString(actual.getHeaderContent(), StandardCharsets.UTF_8), IOUtils.toString(expected.getHeaderContent(), StandardCharsets.UTF_8))) {
                failWithMessage("Expected Header content to be <%s> but was <%s>", IOUtils.toString(expected.getHeaderContent(), StandardCharsets.UTF_8), IOUtils.toString(actual.getHeaderContent(), StandardCharsets.UTF_8));
            }
        }
        if (usedFetchType == MessageMapper.FetchType.FULL) {
            if (!Objects.equal(IOUtils.toString(actual.getBodyContent(), StandardCharsets.UTF_8), IOUtils.toString(expected.getBodyContent(), StandardCharsets.UTF_8))) {
                failWithMessage("Expected Body content to be <%s> but was <%s>", IOUtils.toString(expected.getBodyContent(), StandardCharsets.UTF_8), IOUtils.toString(actual.getBodyContent(), StandardCharsets.UTF_8));
            }
        }
        return this;
    }

    public MessageAssert hasFlags(Flags flags) {
        if (flags.contains(Flags.Flag.ANSWERED) != actual.isAnswered()) {
            failWithMessage("Expected ANSWERED flag to be <%s> but was <%>", flags.contains(Flags.Flag.ANSWERED), actual.isAnswered());
        }
        if (flags.contains(Flags.Flag.DELETED) != actual.isDeleted()) {
            failWithMessage("Expected DELETED flag to be <%s> but was <%>", flags.contains(Flags.Flag.DELETED), actual.isDeleted());
        }
        if (flags.contains(Flags.Flag.DRAFT) != actual.isDraft()) {
            failWithMessage("Expected DRAFT flag to be <%s> but was <%>", flags.contains(Flags.Flag.DRAFT), actual.isDraft());
        }
        if (flags.contains(Flags.Flag.FLAGGED) != actual.isFlagged()) {
            failWithMessage("Expected FLAGGED flag to be <%s> but was <%>", flags.contains(Flags.Flag.FLAGGED), actual.isFlagged());
        }
        if (flags.contains(Flags.Flag.SEEN) != actual.isSeen()) {
            failWithMessage("Expected SEEN flag to be <%s> but was <%>", flags.contains(Flags.Flag.SEEN), actual.isSeen());
        }
        if (flags.contains(Flags.Flag.RECENT) != actual.isRecent()) {
            failWithMessage("Expected RECENT flag to be <%s> but was <%>", flags.contains(Flags.Flag.RECENT), actual.isRecent());
        }
        return this;
    }

}