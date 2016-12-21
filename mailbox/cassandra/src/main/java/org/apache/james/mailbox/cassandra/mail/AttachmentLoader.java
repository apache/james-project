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
package org.apache.james.mailbox.cassandra.mail;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.util.OptionalConverter;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;

public class AttachmentLoader {

    private final AttachmentMapper attachmentMapper;

    public AttachmentLoader(AttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    public Collection<MessageAttachment> getAttachments(Set<CassandraMessageDAO.MessageAttachmentRepresentation> attachmentRepresentations) {
        Map<AttachmentId, Attachment> attachmentsById = attachmentsById(attachmentRepresentations.stream()
            .map(CassandraMessageDAO.MessageAttachmentRepresentation::getAttachmentId)
            .collect(Guavate.toImmutableSet()));

        return attachmentRepresentations.stream()
            .map(representation -> constructMessageAttachment(attachmentsById.get(representation.getAttachmentId()), representation))
            .collect(Guavate.toImmutableSet());
    }

    private MessageAttachment constructMessageAttachment(Attachment attachment, CassandraMessageDAO.MessageAttachmentRepresentation messageAttachmentRepresentation) {
        return MessageAttachment.builder()
                .attachment(attachment)
                .name(messageAttachmentRepresentation.getName().orElse(null))
                .cid(OptionalConverter.toGuava(messageAttachmentRepresentation.getCid()))
                .isInline(messageAttachmentRepresentation.isInline())
                .build();
    }

    @VisibleForTesting Map<AttachmentId, Attachment> attachmentsById(Set<AttachmentId> attachmentIds) {
        return attachmentMapper.getAttachments(attachmentIds).stream()
            .collect(Guavate.toImmutableMap(Attachment::getAttachmentId, Function.identity()));
    }

}
