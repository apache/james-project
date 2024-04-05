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

package org.apache.james.jmap.model.message.view;

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.james.jmap.model.Keywords;
import org.apache.james.jmap.utils.KeywordsCombiner;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

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
            Mono<T> fromMessageResults(Collection<MessageResult> messageResults);
        }

        static void assertOneMessageId(Collection<MessageResult> messageResults) {
            Preconditions.checkArgument(!messageResults.isEmpty(), "MessageResults cannot be empty");
            Preconditions.checkArgument(hasOnlyOneMessageId(messageResults), "MessageResults need to share the same messageId");
        }

        private static boolean hasOnlyOneMessageId(Collection<MessageResult> messageResults) {
            if (messageResults.size() == 1) {
                return true;
            }
            return messageResults
                .stream()
                .map(MessageResult::getMessageId)
                .distinct()
                .count() == 1;
        }

        static Set<MailboxId> getMailboxIds(Collection<MessageResult> messageResults) {
            return messageResults.stream()
                .map(MessageResult::getMailboxId)
                .collect(ImmutableSet.toImmutableSet());
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


        static ImmutableMap<String, String> toHeaderMap(Header header) {
            return header.getFieldsAsMap()
                .entrySet()
                .stream()
                .collect(ImmutableMap.toImmutableMap(entry -> entry.getValue().get(0).getName(),
                    entry -> entry.getValue().stream()
                        .map(Field::getBody)
                        .map(body -> DecoderUtil.decodeEncodedWords(body, DecodeMonitor.SILENT))
                        .collect(Collectors.joining(JMAP_MULTIVALUED_FIELD_DELIMITER))));
        }

        static <T extends MessageView>  Function<Collection<MessageResult>, Mono<T>> toMessageViews(FromMessageResult<T> converter) {
            return messageResults ->
                converter.fromMessageResults(messageResults)
                    .doOnEach(ReactorUtils.logOnError(e -> LOGGER.error("Can not convert MessageResults to Message for {}",
                        messageResults.iterator().next().getMessageId().serialize(),
                        e)))
                    .onErrorResume(e -> Mono.empty());
        }

        static <T extends MessageView> Flux<T> toMessageViews(Flux<MessageResult> messageResults, FromMessageResult<T> converter) {
            return messageResults
                .groupBy(MessageResult::getMessageId)
                .flatMap(Flux::collectList, DEFAULT_CONCURRENCY)
                .filter(Predicate.not(List::isEmpty))
                .flatMap(toMessageViews(converter), DEFAULT_CONCURRENCY);
        }

        static Instant getDateFromHeaderOrInternalDateOtherwise(Message mimeMessage, MessageResult message) {
            return Optional.ofNullable(mimeMessage.getDate())
                .map(Date::toInstant)
                .orElse(message.getInternalDate().toInstant());
        }

        static Message retrieveMessage(MessageFullViewFactory.MetaDataWithContent metaDataWithContent) {
            return metaDataWithContent.getMessage()
                .orElseGet(Throwing.supplier(() ->
                    parse(metaDataWithContent.getContent())).sneakyThrow());
        }

        static Message parse(InputStream messageContent) throws IOException {
            DefaultMessageBuilder defaultMessageBuilder = new DefaultMessageBuilder();
            defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
            return defaultMessageBuilder.parseMessage(messageContent);
        }
    }
}
