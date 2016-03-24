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

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.elasticsearch.ElasticSearchIndexer;
import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.apache.james.mailbox.elasticsearch.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageRange.Type;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

public class ElasticSearchListeningMessageSearchIndex<Id extends MailboxId> extends ListeningMessageSearchIndex<Id> {

    private final static Logger LOGGER = LoggerFactory.getLogger(ElasticSearchListeningMessageSearchIndex.class);
    private final static String ID_SEPARATOR = ":";
    
    private final ElasticSearchIndexer indexer;
    private final ElasticSearchSearcher<Id> searcher;
    private final MessageToElasticSearchJson messageToElasticSearchJson;

    @Inject
    public ElasticSearchListeningMessageSearchIndex(MessageMapperFactory<Id> factory, ElasticSearchIndexer indexer,
        ElasticSearchSearcher<Id> searcher, MessageToElasticSearchJson messageToElasticSearchJson) {
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
    public Iterator<Long> search(MailboxSession session, Mailbox<Id> mailbox, SearchQuery searchQuery) throws MailboxException {
        return searcher.search(mailbox, searchQuery);
    }

    @Override
    public void add(MailboxSession session, Mailbox<Id> mailbox, MailboxMessage<Id> message) throws MailboxException {
        try {
            indexer.indexMessage(indexIdFor(mailbox, message.getUid()), messageToElasticSearchJson.convertToJson(message));
        } catch (Exception e) {
            LOGGER.error("Error when indexing message " + message.getUid(), e);
        }
    }

    @Override
    public void delete(MailboxSession session, Mailbox<Id> mailbox, MessageRange range) throws MailboxException {
        if (range.getType() == Type.ALL) {
            indexer.deleteAllMatchingQuery(termQuery(JsonMessageConstants.MAILBOX_ID, mailbox.getMailboxId().serialize()));
        } else {
            range.forEach(messageId -> {
                try {
                    indexer.deleteMessage(indexIdFor(mailbox, messageId));
                } catch (Exception e) {
                    LOGGER.error("Error when deleting index for message " + messageId, e);
                }
            });
        }
    }

    @Override
    public void update(MailboxSession session, Mailbox<Id> mailbox, List<UpdatedFlags> updatedFlagsList) throws MailboxException {
        try {
            indexer.updateMessages(updatedFlagsList.stream()
                .map(updatedFlags -> createUpdatedDocumentPartFromUpdatedFlags(mailbox, updatedFlags))
                .collect(Collectors.toList()));
        } catch (Exception e) {
            LOGGER.error("Error when updating index on mailbox {}", mailbox.getMailboxId().serialize(), e);
        }
    }

    private ElasticSearchIndexer.UpdatedRepresentation createUpdatedDocumentPartFromUpdatedFlags(Mailbox<Id> mailbox, UpdatedFlags updatedFlags) {
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

    private String indexIdFor(Mailbox<Id> mailbox, long messageId) {
        return String.join(ID_SEPARATOR, mailbox.getMailboxId().serialize(), String.valueOf(messageId));
    }
    
}
