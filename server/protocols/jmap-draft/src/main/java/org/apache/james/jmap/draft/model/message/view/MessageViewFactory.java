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

import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.utils.KeywordsCombiner;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageResult;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;

public interface MessageViewFactory<T extends MessageView> {
    KeywordsCombiner KEYWORDS_COMBINER = new KeywordsCombiner();
    Keywords.KeywordsFactory KEYWORDS_FACTORY = Keywords.lenientFactory();

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
}
