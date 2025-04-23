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

package org.apache.james.mailbox.opensearch.search;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.json.JsonMessageConstants;
import org.apache.james.mailbox.searchhighligt.SearchHighlighter;
import org.apache.james.mailbox.searchhighligt.SearchHighlighterConfiguration;
import org.apache.james.mailbox.searchhighligt.SearchSnippet;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.opensearch.client.opensearch.core.search.Hit;

import com.fasterxml.jackson.databind.node.ObjectNode;

import reactor.core.publisher.Flux;

public class OpenSearchSearchHighlighter implements SearchHighlighter {
    private static class HtmlEscaper {
        // Use a random UUID as part of the placeholder to avoid collision and potential attack bypassing HTML escaping
        private static final UUID RANDOM_UUID = UUID.randomUUID();
        private static final String HIGHLIGHT_OPEN_TAG_PLACEHOLDER = "HIGHLIGHT_OPEN_" + RANDOM_UUID;
        private static final String HIGHLIGHT_CLOSE_TAG_PLACEHOLDER = "HIGHLIGHT_CLOSE_" + RANDOM_UUID;

        public static String escapeHtmlPreservingHighlightTags(String input, String highlightOpenTag, String highlightCloseTag) {
            String withHighlightTagPlaceholders = preserveHighlightTags(input, highlightOpenTag, highlightCloseTag);
            String escaped = escapeHtmlCharacters(withHighlightTagPlaceholders);
            return restoreHighlightTags(escaped, highlightOpenTag, highlightCloseTag);
        }

        private static String preserveHighlightTags(String input, String highlightOpenTag, String highlightCloseTag) {
            return input
                .replace(highlightOpenTag, HIGHLIGHT_OPEN_TAG_PLACEHOLDER)
                .replace(highlightCloseTag, HIGHLIGHT_CLOSE_TAG_PLACEHOLDER);
        }

        private static String escapeHtmlCharacters(String input) {
            // & (ampersand), < (less-than sign), and > (greater-than sign) MUST be HTML escaped following the JMAP specification
            // cf https://jmap.io/spec-mail.html#search-snippets
            return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        }

        private static String restoreHighlightTags(String input, String highlightOpenTag, String highlightCloseTag) {
            return input
                .replace(HIGHLIGHT_OPEN_TAG_PLACEHOLDER, highlightOpenTag)
                .replace(HIGHLIGHT_CLOSE_TAG_PLACEHOLDER, highlightCloseTag);
        }
    }

    public static final String ATTACHMENT_TEXT_CONTENT_FIELD = JsonMessageConstants.ATTACHMENTS + "." + JsonMessageConstants.Attachment.TEXT_CONTENT;
    public static final List<String> SNIPPET_FIELDS = List.of(
        JsonMessageConstants.MESSAGE_ID,
        JsonMessageConstants.SUBJECT,
        JsonMessageConstants.TEXT_BODY,
        ATTACHMENT_TEXT_CONTENT_FIELD);

    private final OpenSearchSearcher openSearchSearcher;
    private final StoreMailboxManager storeMailboxManager;
    private final MessageId.Factory messageIdFactory;
    private final SearchHighlighterConfiguration searchHighlighterConfiguration;

    @Inject
    @Singleton
    public OpenSearchSearchHighlighter(OpenSearchSearcher openSearchSearcher, StoreMailboxManager storeMailboxManager, MessageId.Factory messageIdFactory,
                                       SearchHighlighterConfiguration searchHighlighterConfiguration) {
        this.openSearchSearcher = openSearchSearcher;
        this.storeMailboxManager = storeMailboxManager;
        this.messageIdFactory = messageIdFactory;
        this.searchHighlighterConfiguration = searchHighlighterConfiguration;
    }

    @Override
    public Flux<SearchSnippet> highlightSearch(List<MessageId> messageIds, MultimailboxesSearchQuery expression, MailboxSession session) {
        if (messageIds.isEmpty() || expression.getSearchQuery().getCriteria().isEmpty()) {
            return Flux.empty();
        }

        return storeMailboxManager.getInMailboxIds(expression, session)
            .collectList()
            .flatMapMany(mailboxIds -> highlightSearch(mailboxIds, expression.getSearchQuery(), messageIds.size()));
    }

    private Flux<SearchSnippet> highlightSearch(List<MailboxId> mailboxIds, SearchQuery query, int limit) {
        return openSearchSearcher.search(mailboxIds, query, Optional.of(limit), SNIPPET_FIELDS, OpenSearchSearcher.SEARCH_HIGHLIGHT)
            .map(this::buildSearchSnippet);
    }

    private SearchSnippet buildSearchSnippet(Hit<ObjectNode> searchResult) {
        MessageId messageId  = Optional.ofNullable(searchResult.fields().get(JsonMessageConstants.MESSAGE_ID))
            .map(jsonData -> jsonData.toJson().asJsonArray().getString(0))
            .map(messageIdFactory::fromString)
            .orElseThrow(() -> new IllegalStateException("Can not extract MessageID for search result: " + searchResult.id()));

        Map<String, List<String>> highlightHit = searchResult.highlight();

        Optional<String> highlightedSubject =  Optional.ofNullable(highlightHit.get(JsonMessageConstants.SUBJECT))
            .map(List::getFirst)
            .map(subject -> HtmlEscaper.escapeHtmlPreservingHighlightTags(subject, searchHighlighterConfiguration.preTagFormatter(), searchHighlighterConfiguration.postTagFormatter()));
        Optional<String> highlightedTextBody = Optional.ofNullable(highlightHit.get(JsonMessageConstants.TEXT_BODY))
            .or(() -> Optional.ofNullable(highlightHit.get(JsonMessageConstants.HTML_BODY)))
            .or(() -> Optional.ofNullable(highlightHit.get(ATTACHMENT_TEXT_CONTENT_FIELD)))
            .map(List::getFirst)
            .map(textBody -> HtmlEscaper.escapeHtmlPreservingHighlightTags(textBody, searchHighlighterConfiguration.preTagFormatter(), searchHighlighterConfiguration.postTagFormatter()));

        return new SearchSnippet(messageId, highlightedSubject, highlightedTextBody);
    }
}
