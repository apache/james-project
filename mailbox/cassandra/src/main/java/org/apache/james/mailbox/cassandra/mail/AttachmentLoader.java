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
import java.util.Set;
import java.util.Map;
import java.util.function.Function;

import org.apache.james.mailbox.cassandra.mail.utils.MapMerger;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.util.OptionalConverter;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;

public class AttachmentLoader {

    private final AttachmentMapper attachmentMapper;

    public AttachmentLoader(AttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    public Collection<org.apache.james.mailbox.model.MessageAttachment> getAttachments(Set<CassandraMessageDAO.MessageAttachment> attachmentsById) {

        Map<AttachmentId, CassandraMessageDAO.MessageAttachment> attachmentIds = attachmentsById.stream()
                .collect(Guavate.toImmutableMap(CassandraMessageDAO.MessageAttachment::getAttachmentId, Function.identity()));

        Map<AttachmentId, Attachment> attachmentByIdMap = attachmentsById(attachmentIds.keySet());

        return MapMerger.merge(attachmentByIdMap, attachmentIds, this::constructMessageAttachment).values();
    }

    private org.apache.james.mailbox.model.MessageAttachment constructMessageAttachment(Attachment attachment, CassandraMessageDAO.MessageAttachment messageAttachmentById) {
        return org.apache.james.mailbox.model.MessageAttachment.builder()
                .attachment(attachment)
                .name(messageAttachmentById.getName().orElse(null))
                .cid(OptionalConverter.toGuava(messageAttachmentById.getCid()))
                .isInline(messageAttachmentById.isInline())
                .build();
    }

    @VisibleForTesting Map<AttachmentId,Attachment> attachmentsById(Set<AttachmentId> attachmentIds) {
        return attachmentMapper.getAttachments(attachmentIds).stream()
            .collect(Guavate.toImmutableMap(Attachment::getAttachmentId, Function.identity()));
    }

}
