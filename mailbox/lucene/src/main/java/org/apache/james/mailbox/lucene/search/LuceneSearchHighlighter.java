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

package org.apache.james.mailbox.lucene.search;

import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.ATTACHMENT_TEXT_CONTENT_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.BODY_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.MESSAGE_ID_FIELD;
import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.SUBJECT_FIELD;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.ConjunctionCriterion;
import org.apache.james.mailbox.model.SearchQuery.SubjectCriterion;
import org.apache.james.mailbox.model.SearchQuery.TextCriterion;
import org.apache.james.mailbox.searchhighligt.SearchHighlighter;
import org.apache.james.mailbox.searchhighligt.SearchHighlighterConfiguration;
import org.apache.james.mailbox.searchhighligt.SearchSnippet;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class LuceneSearchHighlighter implements SearchHighlighter {

    private static Analyzer defaultAnalyzer() {
        return new StandardAnalyzer();
    }

    private final LuceneMessageSearchIndex luceneMessageSearchIndex;
    private final Analyzer analyzer;
    private final Formatter formatter;
    private final MessageId.Factory messageIdFactory;
    private final SearchHighlighterConfiguration configuration;
    private final StoreMailboxManager storeMailboxManager;

    public LuceneSearchHighlighter(LuceneMessageSearchIndex luceneMessageSearchIndex,
                                   SearchHighlighterConfiguration searchHighlighterConfiguration,
                                   MessageId.Factory messageIdFactory, StoreMailboxManager storeMailboxManager,
                                   Analyzer analyzer) {
        this.luceneMessageSearchIndex = luceneMessageSearchIndex;
        this.messageIdFactory = messageIdFactory;
        this.analyzer = analyzer;
        this.configuration = searchHighlighterConfiguration;
        this.storeMailboxManager = storeMailboxManager;
        this.formatter = new SimpleHTMLFormatter(searchHighlighterConfiguration.preTagFormatter(), searchHighlighterConfiguration.postTagFormatter());
    }

    @Inject
    @Singleton
    public LuceneSearchHighlighter(LuceneMessageSearchIndex luceneMessageSearchIndex,
                                   SearchHighlighterConfiguration searchHighlighterConfiguration,
                                   MessageId.Factory messageIdFactory,
                                   StoreMailboxManager storeMailboxManager) {
        this(luceneMessageSearchIndex,
            searchHighlighterConfiguration,
            messageIdFactory,
            storeMailboxManager,
            defaultAnalyzer());
    }

    @Override
    public Flux<SearchSnippet> highlightSearch(List<MessageId> messageIds, MultimailboxesSearchQuery expression, MailboxSession session) {
        if (messageIds.isEmpty() || expression.getSearchQuery().getCriteria().isEmpty()) {
            return Flux.empty();
        }
        return storeMailboxManager.getInMailboxIds(expression, session)
            .collectList()
            .flatMapMany(inMailboxIdsAccessible -> highlightSearch(inMailboxIdsAccessible, expression.getSearchQuery(), messageIds));
    }

    private Flux<SearchSnippet> highlightSearch(Collection<MailboxId> mailboxIds, SearchQuery searchQuery, List<MessageId> messageIds) {
        int limit = messageIds.size();
        return Mono.fromCallable(() -> luceneMessageSearchIndex.searchDocument(mailboxIds, searchQuery, limit)).flatMapMany(Flux::fromIterable)
            .map(document -> Throwing.supplier(() -> buildSearchSnippet(document, searchQuery)).get())
            .filter(searchSnippet -> messageIds.contains(searchSnippet.messageId()))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Highlighter highlighter(SearchQuery searchQuery) {
        Query query = buildQueryFromSearchQuery(searchQuery);
        QueryScorer scorer = new QueryScorer(query);
        Highlighter highlighter = new Highlighter(formatter, scorer);
        highlighter.setEncoder(new RelaxedHTMLEncoder());
        highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer, configuration.fragmentSize()));
        return highlighter;
    }

    private Query buildQueryFromSearchQuery(SearchQuery searchQuery) {
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

        searchQuery.getCriteria().stream()
            .map(this::buildQueryFromCriterion)
            .flatMap(Optional::stream)
            .forEach(query -> queryBuilder.add(query, BooleanClause.Occur.SHOULD));

        return queryBuilder.build();
    }

    private Optional<Query> buildQueryFromCriterion(SearchQuery.Criterion criterion) {
        if (criterion instanceof TextCriterion textCriterion) {
            return Optional.of(buildQuery(BODY_FIELD, textCriterion.getOperator().getValue().toLowerCase(Locale.US)));
        } else if (criterion instanceof SubjectCriterion subjectCriterion) {
            return Optional.of(buildQuery(SUBJECT_FIELD, subjectCriterion.getSubject().toLowerCase(Locale.US)));
        } else if (criterion instanceof ConjunctionCriterion conjunctionCriterion && !conjunctionCriterion.getType().equals(SearchQuery.Conjunction.NOR)) {
            BooleanQuery.Builder conQuery = new BooleanQuery.Builder();
            conjunctionCriterion.getCriteria().stream()
                .map(this::buildQueryFromCriterion)
                .flatMap(Optional::stream)
                .forEach(query -> conQuery.add(query, BooleanClause.Occur.SHOULD));
            return Optional.of(conQuery.build());
        }
        return Optional.empty();
    }

    private Query buildQuery(String field, String queryValue) {
        QueryParser parser = new QueryParser(field, analyzer);
        return Throwing.supplier(() -> parser.parse(queryValue)).get();
    }

    private SearchSnippet buildSearchSnippet(Document doc, SearchQuery searchQuery) {
        MessageId messageId = messageIdFactory.fromString(doc.get(MESSAGE_ID_FIELD));
        Optional<String> highlightedSubject = Optional.ofNullable(getHighlightedSubject(doc, searchQuery));
        Optional<String> highlightedBody = Optional.ofNullable(getHighlightedBody(doc, searchQuery))
            .or(() -> getHighlightAttachmentTextBody(doc, searchQuery));

        return new SearchSnippet(messageId, highlightedSubject, highlightedBody);
    }

    private String getHighlightedSubject(Document doc, SearchQuery searchQuery) {
        return Optional.ofNullable(doc.get(SUBJECT_FIELD))
            .map(Throwing.function(subject -> highlighter(searchQuery).getBestFragment(analyzer, SUBJECT_FIELD, subject)))
            .orElse(null);
    }

    private String getHighlightedBody(Document doc, SearchQuery searchQuery) {
        return Optional.ofNullable(doc.get(BODY_FIELD))
            .map(Throwing.function(body -> highlighter(searchQuery).getBestFragment(analyzer, BODY_FIELD, body)))
            .orElse(null);
    }

    private Optional<String> getHighlightAttachmentTextBody(Document doc, SearchQuery searchQuery) {
        Highlighter highlighter = highlighter(searchQuery);
        return Stream.ofNullable(doc.getFields(ATTACHMENT_TEXT_CONTENT_FIELD)).flatMap(Arrays::stream)
            .map(IndexableField::stringValue)
            .map(Throwing.function(contentType -> highlighter.getBestFragment(analyzer, ATTACHMENT_TEXT_CONTENT_FIELD, contentType)))
            .findFirst();
    }
}
