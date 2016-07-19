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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.elasticsearch.ElasticSearchIndexer;
import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.apache.james.mailbox.elasticsearch.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;

public class ElasticSearchListeningMessageSearchIndex extends ListeningMessageSearchIndex {

    private final static Logger LOGGER = LoggerFactory.getLogger(ElasticSearchListeningMessageSearchIndex.class);
    private final static String ID_SEPARATOR = ":";
    
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
    public Iterator<Long> search(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) throws MailboxException {
        MailboxId mailboxId = mailbox.getMailboxId();
        return searcher
                .search(ImmutableList.of(mailboxId), searchQuery)
                .get(mailboxId)
                .iterator();
    }
    
    @Override
    public Map<MailboxId, Collection<Long>> search(MailboxSession session, MultimailboxesSearchQuery searchQuery)
            throws MailboxException {
        return searcher.search(searchQuery.getMailboxIds(), searchQuery.getSearchQuery()).asMap();
    }

    @Override
    public void add(MailboxSession session, Mailbox mailbox, MailboxMessage message) throws MailboxException {
        try {
            indexer.indexMessage(indexIdFor(mailbox, message.getUid()), messageToElasticSearchJson.convertToJson(message));
        } catch (Exception e) {
            LOGGER.error("Error when indexing message " + message.getUid(), e);
        }
    }

    @Override
    public void delete(MailboxSession session, Mailbox mailbox, List<Long> expungedUids) throws MailboxException {
        try {
            indexer.deleteMessages(expungedUids.stream()
                .map(uid ->  indexIdFor(mailbox, uid))
                .collect(Collectors.toList()));
        } catch (Exception e) {
            LOGGER.error("Error when deleting messages {} in mailbox {} from index", mailbox.getMailboxId().serialize(), expungedUids, e);
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

    private String indexIdFor(Mailbox mailbox, long messageId) {
        return String.join(ID_SEPARATOR, mailbox.getMailboxId().serialize(), String.valueOf(messageId));
    }
    
}
