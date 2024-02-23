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

package org.apache.james.mailbox.opensearch.json;

import static org.apache.james.mailbox.opensearch.json.IndexableMessage.DATE_TIME_FORMATTER;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.opensearch.IndexAttachments;
import org.apache.james.mailbox.opensearch.IndexBody;
import org.apache.james.mailbox.opensearch.IndexHeaders;
import org.apache.james.mailbox.opensearch.OpenSearchMailboxConfiguration;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class MessageToOpenSearchJson {

    private final ObjectMapper mapper;
    private final TextExtractor textExtractor;
    private final ZoneId zoneId;
    private final IndexAttachments indexAttachments;
    private final IndexHeaders indexHeaders;
    private final IndexBody indexBody;

    public MessageToOpenSearchJson(TextExtractor textExtractor, ZoneId zoneId, IndexAttachments indexAttachments, IndexHeaders indexHeaders) {
        this(textExtractor, zoneId, indexAttachments, indexHeaders, IndexBody.YES);
    }

    public MessageToOpenSearchJson(TextExtractor textExtractor, ZoneId zoneId, IndexAttachments indexAttachments, IndexHeaders indexHeaders, IndexBody indexBody) {
        this.textExtractor = textExtractor;
        this.zoneId = zoneId;
        this.indexAttachments = indexAttachments;
        this.indexHeaders = indexHeaders;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new GuavaModule());
        this.mapper.registerModule(new Jdk8Module());
        this.indexBody = indexBody;
    }

    @Inject
    public MessageToOpenSearchJson(TextExtractor textExtractor, OpenSearchMailboxConfiguration configuration) {
        this(textExtractor, ZoneId.systemDefault(), configuration.getIndexAttachment(), configuration.getIndexHeaders(), configuration.getIndexBody());
    }

    public Mono<String> convertToJson(MailboxMessage message) {
        Preconditions.checkNotNull(message);

        return IndexableMessage.builder()
            .message(message)
            .extractor(textExtractor)
            .zoneId(zoneId)
            .indexAttachments(indexAttachments)
            .indexHeaders(indexHeaders)
            .indexBody(indexBody)
            .build()
            .map(Throwing.function(mapper::writeValueAsString));
    }

    public Mono<String> convertToJsonWithoutAttachment(MailboxMessage message) {
        return IndexableMessage.builder()
            .message(message)
            .extractor(textExtractor)
            .zoneId(zoneId)
            .indexAttachments(IndexAttachments.NO)
            .indexHeaders(indexHeaders)
            .indexBody(indexBody)
            .build()
            .map(Throwing.function(mapper::writeValueAsString));
    }

    public String getUpdatedJsonMessagePart(Flags flags, ModSeq modSeq) throws JsonProcessingException {
        Preconditions.checkNotNull(flags);
        return mapper.writeValueAsString(new MessageUpdateJson(flags, modSeq));
    }

    public ObjectNode updateMailboxId(ObjectNode origin, MailboxId newMailboxId) {
        origin.put(JsonMessageConstants.MAILBOX_ID, newMailboxId.serialize());
        return origin;
    }

    public ObjectNode updateSaveDate(ObjectNode origin, Date newSaveDate) {
        origin.put(JsonMessageConstants.SAVE_DATE, DATE_TIME_FORMATTER.format(ZonedDateTime.ofInstant(newSaveDate.toInstant(), zoneId)));
        return origin;
    }

    public ObjectNode updateMessageUid(ObjectNode origin, MessageUid newMessageUid) {
        origin.put(JsonMessageConstants.UID, newMessageUid.asLong());
        return origin;
    }

    public String toString(ObjectNode objectNode) {
        return Throwing.supplier(() -> mapper.writeValueAsString(objectNode)).get();
    }
}
