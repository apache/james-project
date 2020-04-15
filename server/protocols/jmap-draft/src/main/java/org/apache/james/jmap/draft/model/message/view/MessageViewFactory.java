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

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.utils.KeywordsCombiner;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.util.MimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimaps;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MessageViewFactory<T extends MessageView> {

    Logger LOGGER = LoggerFactory.getLogger(MessageViewFactory.class);

    KeywordsCombiner KEYWORDS_COMBINER = new KeywordsCombiner();
    Keywords.KeywordsFactory KEYWORDS_FACTORY = Keywords.lenientFactory();
    String JMAP_MULTIVALUED_FIELD_DELIMITER = "\n";

    Flux<T> fromMessageIds(List<MessageId> messageIds, MailboxSession mailboxSession);

    class Helpers {
        interface FromMessageResult<T extends MessageView> {
            T fromMessageResults(Collection<MessageResult> messageResults) throws MailboxException, IOException;
        }

        static void assertOneMessageId(Collection<MessageResult> messageResults) {
            Preconditions.checkArgument(!messageResults.isEmpty(), "MessageResults cannot be empty");
            Preconditions.checkArgument(hasOnlyOneMessageId(messageResults), "MessageResults need to share the same messageId");
        }

        private static boolean hasOnlyOneMessageId(Collection<MessageResult> messageResults) {
            return messageResults
                .stream()
                .map(MessageResult::getMessageId)
                .distinct()
                .count() == 1;
        }

        static List<MailboxId> getMailboxIds(Collection<MessageResult> messageResults) {
            return messageResults.stream()
                .map(MessageResult::getMailboxId)
                .distinct()
                .collect(Guavate.toImmutableList());
        }

        static Keywords getKeywords(Collection<MessageResult> messageResults) {
            return messageResults.stream()
                .map(MessageResult::getFlags)
                .map(KEYWORDS_FACTORY::fromFlags)
                .reduce(KEYWORDS_COMBINER)
                .get();
        }

        static String getHeaderValue(org.apache.james.mime4j.dom.Message message, String header) {
            Field field = message.getHeader().getField(header);
            if (field == null) {
                return null;
            }
            return field.getBody();
        }

        static ImmutableMap<String, String> toHeaderMap(List<Field> fields) {
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

        static <T extends MessageView>  Function<Collection<MessageResult>, Mono<T>> toMessageViews(FromMessageResult<T> converter) {
            return messageResults -> {
                try {
                    return Mono.just(converter.fromMessageResults(messageResults));
                } catch (Exception e) {
                    LOGGER.error("Can not convert MessageResults to Message for {}", messageResults.iterator().next().getMessageId().serialize(), e);
                    return Mono.empty();
                }
            };
        }

        static <T extends MessageView> Flux<T> toMessageViews(Flux<MessageResult> messageResults, FromMessageResult<T> converter) {
            return messageResults
                .groupBy(MessageResult::getMessageId)
                .flatMap(Flux::collectList)
                .filter(list -> !list.isEmpty())
                .flatMap(toMessageViews(converter));
        }

        static Instant getDateFromHeaderOrInternalDateOtherwise(Message mimeMessage, MessageResult message) {
            return Optional.ofNullable(mimeMessage.getDate())
                .map(Date::toInstant)
                .orElse(message.getInternalDate().toInstant());
        }

        static Message parse(InputStream messageContent) throws IOException {
            return Message.Builder
                .of()
                .use(MimeConfig.PERMISSIVE)
                .parse(messageContent)
                .build();
        }
    }
}
