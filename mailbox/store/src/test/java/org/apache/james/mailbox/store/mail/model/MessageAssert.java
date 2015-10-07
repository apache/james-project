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

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.assertj.core.api.AbstractAssert;

import javax.mail.Flags;
import java.io.IOException;

public class MessageAssert extends AbstractAssert<MessageAssert, Message<?>> {

    public MessageAssert(Message<?> actual) {
        super(actual, MessageAssert.class);
    }

    public static MessageAssert assertThat(Message<?> actual) {
        return new MessageAssert(actual);
    }

    public MessageAssert isEqualTo(Message<?> expected, MessageMapper.FetchType usedFetchType) throws IOException {
        isNotNull();
        if (!equals(actual.getMailboxId(), expected.getMailboxId())) {
            failWithMessage("Expected Mailbox ID to be <%s> but was <%s>", expected.getMailboxId().toString(), actual.getMailboxId().toString());
        }
        if (!equals(actual.getUid(), expected.getUid())) {
            failWithMessage("Expected UID to be <%s> but was <%s>", expected.getUid(), actual.getUid());
        }
        if (!equals(actual.getInternalDate(), expected.getInternalDate())) {
            failWithMessage("Expected Internal Date to be <%s> but was <%s>", expected.getInternalDate(), actual.getInternalDate());
        }
        if (!equals(actual.getBodyOctets(), expected.getBodyOctets())) {
            failWithMessage("Expected Body octet to be <%s> but was <%s>", expected.getBodyOctets(), actual.getBodyOctets());
        }
        if (!equals(actual.getMediaType(), expected.getMediaType())) {
            failWithMessage("Expected Media type to be <%s> but was <%s>", expected.getBodyOctets(), actual.getBodyOctets());
        }
        if (!equals(actual.getSubType(), expected.getSubType())) {
            failWithMessage("Expected Sub type to be <%s> but was <%s>", expected.getBodyOctets(), actual.getBodyOctets());
        }
        if (usedFetchType == MessageMapper.FetchType.Full) {
            if (!equals(actual.getFullContentOctets(), expected.getFullContentOctets())) {
                failWithMessage("Expected Message size to be <%s> but was <%s>", expected.getFullContentOctets(), actual.getFullContentOctets());
            }
            if (!equals(IOUtils.toString(actual.getFullContent()), IOUtils.toString(expected.getFullContent()))) {
                failWithMessage("Expected Full content to be <%s> but was <%s>", IOUtils.toString(actual.getFullContent()), IOUtils.toString(expected.getFullContent()));
            }
        }
        if (usedFetchType == MessageMapper.FetchType.Full || usedFetchType == MessageMapper.FetchType.Headers) {
            if (!equals(IOUtils.toString(actual.getHeaderContent()), IOUtils.toString(expected.getHeaderContent()))) {
                failWithMessage("Expected Header content to be <%s> but was <%s>", IOUtils.toString(actual.getHeaderContent()), IOUtils.toString(expected.getHeaderContent()));
            }
        }
        if (usedFetchType == MessageMapper.FetchType.Full || usedFetchType == MessageMapper.FetchType.Body) {
            if (!equals(IOUtils.toString(actual.getBodyContent()), IOUtils.toString(expected.getBodyContent()))) {
                failWithMessage("Expected Body content to be <%s> but was <%s>", IOUtils.toString(actual.getBodyContent()), IOUtils.toString(expected.getBodyContent()));
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

    private boolean equals(Object object1, Object object2) {
        if ( object1 == null && object2 == null ) {
            return true;
        }
        return ( object1 != null ) && object1.equals(object2);
    }
}