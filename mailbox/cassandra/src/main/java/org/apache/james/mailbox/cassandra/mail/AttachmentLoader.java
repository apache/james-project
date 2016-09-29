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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.util.OptionalConverter;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;

public class AttachmentLoader {

    private final AttachmentMapper attachmentMapper;

    public AttachmentLoader(AttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    public Stream<MessageAttachment> getAttachments(List<MessageAttachmentById> attachmentsById) {
        Map<AttachmentId, Attachment> attachmentByIdMap = attachmentsById(attachmentsById.stream()
                .map(MessageAttachmentById::getAttachmentId)
                .collect(Guavate.toImmutableList()));
        return attachmentsById.stream()
                .map(Throwing.function(attachment ->
                    MessageAttachment.builder()
                        .attachment(attachmentByIdMap.get(attachment.getAttachmentId()))
                        .name(attachment.getName().orElse(null))
                        .cid(OptionalConverter.toGuava(attachment.getCid()))
                        .isInline(attachment.isInline())
                        .build())
                );
    }

    @VisibleForTesting Map<AttachmentId,Attachment> attachmentsById(List<AttachmentId> attachmentIds) {
        return attachmentMapper.getAttachments(attachmentIds).stream()
            .collect(toMapRemovingDuplicateKeys(Attachment::getAttachmentId, Function.identity()));
    }

    private Collector<Attachment, Map<AttachmentId, Attachment>, Map<AttachmentId, Attachment>> toMapRemovingDuplicateKeys(
            Function<Attachment, AttachmentId> keyMapper,
            Function<Attachment, Attachment> valueMapper) {
        return Collector.of(HashMap::new,
                (acc, v) -> acc.put(keyMapper.apply(v), valueMapper.apply(v)),
                (map1, map2) -> {
                    map1.putAll(map2);
                    return map1;
                },
                Function.identity()
                );
    }
}
