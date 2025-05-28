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

package org.apache.james.mailbox.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mime4j.dom.Message;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;

import reactor.core.publisher.Flux;

class OpenSearchOptimizeMoveAndFuzzySearchIntegrationTest extends OpenSearchIntegrationTest {

    @Override
    protected OpenSearchMailboxConfiguration openSearchMailboxConfiguration() {
        return OpenSearchMailboxConfiguration.builder()
            .optimiseMoves(true)
            .textFuzzinessSearch(true)
            .build();
    }

    @Test
    void searchShouldBeLenientOnUserTypo() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo(recipient)
                    .setSubject("fuzzy subject")
                    .setBody("fuzzy body", StandardCharsets.UTF_8)),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.subject("fuzzi")), session)).toStream())
            .containsExactly(composedMessageId.getUid());
        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.bodyContains("fuzzi")), session)).toStream())
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    void searchShouldBeLenientOnAdjacentCharactersTranspositions() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo(recipient)
                    .setSubject("subject")
                    .setBody("body", StandardCharsets.UTF_8)),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.subject("subjetc")), session)).toStream())
            .containsExactly(composedMessageId.getUid());
        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.bodyContains("boyd")), session)).toStream())
            .containsExactly(composedMessageId.getUid());
    }

    @Disabled("Fuzzyness makes the results wider")
    @Override
    void shouldMatchFileExtension() {

    }
}