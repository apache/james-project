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
package org.apache.james.mailbox.elasticsearch.events;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.mailbox.MailboxManager.MessageCapabilities;
import org.apache.james.mailbox.MailboxManager.SearchCapabilities;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.apache.james.mailbox.elasticsearch.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class ElasticSearchListeningMessageSearchIndex extends ListeningMessageSearchIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchListeningMessageSearchIndex.class);
    private static final String ID_SEPARATOR = ":";

    private final ElasticSearchIndexer indexer;
    private final ElasticSearchSearcher searcher;
    private final MessageToElasticSearchJson messageToElasticSearchJson;

    @Inject
    public ElasticSearchListeningMessageSearchIndex(MessageMapperFactory factory, ElasticSearchIndexer indexer,
        ElasticSearchSearcher searcher, MessageToElasticSearchJson messageToElasticSearchJson) {
        super(factory);
        this.indexer = indexer;
        this.messageToElasticSearchJson = messageToElasticSearchJson;
        this.searcher = searcher;
    }

    @Override
    public ListenerType getType() {
        return ListenerType.ONCE;
    }

    @Override
    public EnumSet<SearchCapabilities> getSupportedCapabilities(EnumSet<MessageCapabilities> messageCapabilities) {
        return EnumSet.of(
            SearchCapabilities.MultimailboxSearch,
            SearchCapabilities.Text,
            SearchCapabilities.FullText,
            SearchCapabilities.Attachment,
            SearchCapabilities.PartialEmailMatch);
    }
    
    @Override
    public Iterator<MessageUid> search(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) throws MailboxException {
        Preconditions.checkArgument(session != null, "'session' is mandatory");
        Optional<Long> noLimit = Optional.empty();
        return searcher
                .search(ImmutableList.of(mailbox.getMailboxId()), searchQuery, noLimit)
                .map(SearchResult::getMessageUid)
                .iterator();
    }
    
    @Override
    public List<MessageId> search(MailboxSession session, Collection<MailboxId> mailboxIds, SearchQuery searchQuery, long limit)
            throws MailboxException {
        Preconditions.checkArgument(session != null, "'session' is mandatory");

        if (mailboxIds.isEmpty()) {
            return ImmutableList.of();
        }

        return searcher.search(mailboxIds, searchQuery, Optional.empty())
            .peek(this::logIfNoMessageId)
            .map(SearchResult::getMessageId)
            .map(Optional::get)
            .distinct()
            .limit(limit)
            .collect(Guavate.toImmutableList());
    }

    @Override
    public void add(MailboxSession session, Mailbox mailbox, MailboxMessage message) throws MailboxException {
        try {
            LOGGER.info("Indexing mailbox {}-{} of user {} on message {}",
                    mailbox.getName(),
                    mailbox.getMailboxId(),
                    session.getUser().getUserName(),
                    message.getUid());
            indexer.indexMessage(indexIdFor(mailbox, message.getUid()), messageToElasticSearchJson.convertToJson(message, ImmutableList.of(session.getUser())));
        } catch (Exception e) {
            try {
                LOGGER.warn("Indexing mailbox {}-{} of user {} on message {} without attachments ",
                        mailbox.getName(),
                        mailbox.getMailboxId().serialize(),
                        session.getUser().getUserName(),
                        message.getUid(),
                        e);
                indexer.indexMessage(indexIdFor(mailbox, message.getUid()), messageToElasticSearchJson.convertToJsonWithoutAttachment(message, ImmutableList.of(session.getUser())));
            } catch (JsonProcessingException e1) {
                LOGGER.error("Error when indexing mailbox {}-{} of user {} on message {} without its attachment",
                        mailbox.getName(),
                        mailbox.getMailboxId().serialize(),
                        session.getUser().getUserName(),
                        message.getUid(),
                        e1);
            }
        }
    }
    
    @Override
    public void delete(MailboxSession session, Mailbox mailbox, List<MessageUid> expungedUids) throws MailboxException {
        try {
            indexer.deleteMessages(expungedUids.stream()
                .map(uid ->  indexIdFor(mailbox, uid))
                .collect(Collectors.toList()));
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Error when deleting messages {} in mailbox {} from index", mailbox.getMailboxId().serialize(), expungedUids.toArray(), e);
            }
        }
    }

    @Override
    public void deleteAll(MailboxSession session, Mailbox mailbox) throws MailboxException {
        try {
            indexer.deleteAllMatchingQuery(
                termQuery(
                    JsonMessageConstants.MAILBOX_ID,
                    mailbox.getMailboxId().serialize()));
        } catch (Exception e) {
            LOGGER.error("Error when deleting all messages in mailbox {}", mailbox.getMailboxId().serialize(), e);
        }
    }

    @Override
    public void update(MailboxSession session, Mailbox mailbox, List<UpdatedFlags> updatedFlagsList) throws MailboxException {
        try {
            indexer.updateMessages(updatedFlagsList.stream()
                .map(updatedFlags -> createUpdatedDocumentPartFromUpdatedFlags(mailbox, updatedFlags))
                .collect(Collectors.toList()));
        } catch (Exception e) {
            LOGGER.error("Error when updating index on mailbox {}", mailbox.getMailboxId().serialize(), e);
        }
    }

    private ElasticSearchIndexer.UpdatedRepresentation createUpdatedDocumentPartFromUpdatedFlags(Mailbox mailbox, UpdatedFlags updatedFlags) {
        try {
            return new ElasticSearchIndexer.UpdatedRepresentation(
                indexIdFor(mailbox, updatedFlags.getUid()),
                    messageToElasticSearchJson.getUpdatedJsonMessagePart(
                        updatedFlags.getNewFlags(),
                        updatedFlags.getModSeq()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error while creating updatedDocumentParts", e);
        }
    }

    private String indexIdFor(Mailbox mailbox, MessageUid uid) {
        return String.join(ID_SEPARATOR, mailbox.getMailboxId().serialize(), String.valueOf(uid.asLong()));
    }

    private void logIfNoMessageId(SearchResult searchResult) {
        if (!searchResult.getMessageId().isPresent()) {
            LOGGER.error("No messageUid for {} in mailbox {}", searchResult.getMessageUid(), searchResult.getMailboxId());
        }
    }

}
