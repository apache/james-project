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

package org.apache.james.jmap.draft.model.message.view;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.james.jmap.draft.model.Emailer;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.utils.KeywordsCombiner;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.util.MimeUtil;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimaps;

public interface MessageViewFactory<T extends MessageView> {
    KeywordsCombiner KEYWORDS_COMBINER = new KeywordsCombiner();
    Keywords.KeywordsFactory KEYWORDS_FACTORY = Keywords.lenientFactory();
    String JMAP_MULTIVALUED_FIELD_DELIMITER = "\n";

    T fromMessageResults(Collection<MessageResult> messageResults) throws MailboxException;

    default void assertOneMessageId(Collection<MessageResult> messageResults) {
        Preconditions.checkArgument(!messageResults.isEmpty(), "MessageResults cannot be empty");
        Preconditions.checkArgument(hasOnlyOneMessageId(messageResults), "MessageResults need to share the same messageId");
    }

    default boolean hasOnlyOneMessageId(Collection<MessageResult> messageResults) {
        return messageResults
            .stream()
            .map(MessageResult::getMessageId)
            .distinct()
            .count() == 1;
    }

    default List<MailboxId> getMailboxIds(Collection<MessageResult> messageResults) {
        return messageResults.stream()
                .map(MessageResult::getMailboxId)
                .distinct()
                .collect(Guavate.toImmutableList());
    }

    default Keywords getKeywords(Collection<MessageResult> messageResults) {
        return messageResults.stream()
                .map(MessageResult::getFlags)
                .map(KEYWORDS_FACTORY::fromFlags)
                .reduce(KEYWORDS_COMBINER)
                .get();
    }

    default String getHeader(org.apache.james.mime4j.dom.Message message, String header) {
        Field field = message.getHeader().getField(header);
        if (field == null) {
            return null;
        }
        return field.getBody();
    }

    default ImmutableMap<String, String> toMap(List<Field> fields) {
        Function<Map.Entry<String, Collection<Field>>, String> bodyConcatenator = fieldListEntry -> fieldListEntry.getValue()
            .stream()
            .map(Field::getBody)
            .map(MimeUtil::unscrambleHeaderValue)
            .collect(Collectors.toList())
            .stream()
            .collect(Collectors.joining(JMAP_MULTIVALUED_FIELD_DELIMITER));

        return Multimaps.index(fields, Field::getName)
            .asMap()
            .entrySet()
            .stream()
            .collect(Guavate.toImmutableMap(Map.Entry::getKey, bodyConcatenator));
    }

    default Emailer firstFromMailboxList(MailboxList list) {
        if (list == null) {
            return null;
        }
        return list.stream()
            .map(this::fromMailbox)
            .findFirst()
            .orElse(null);
    }

    default Emailer fromMailbox(Mailbox mailbox) {
        return Emailer.builder()
            .name(getNameOrAddress(mailbox))
            .email(mailbox.getAddress())
            .allowInvalid()
            .build();
    }

    default String getNameOrAddress(Mailbox mailbox) {
        if (mailbox.getName() != null) {
            return mailbox.getName();
        }
        return mailbox.getAddress();
    }

    default ImmutableList<Emailer> fromAddressList(AddressList list) {
        if (list == null) {
            return ImmutableList.of();
        }
        return list.flatten()
            .stream()
            .map(this::fromMailbox)
            .collect(Guavate.toImmutableList());
    }
}
