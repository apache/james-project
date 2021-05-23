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

package org.apache.james.jmap.draft.methods;

import java.util.List;

import javax.inject.Inject;

import org.apache.james.jmap.draft.exceptions.AttachmentsNotFoundException;
import org.apache.james.jmap.draft.model.Attachment;
import org.apache.james.jmap.draft.model.BlobId;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.predicates.ThrowingPredicate;
import com.github.steveash.guavate.Guavate;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class AttachmentChecker {
    private final AttachmentManager attachmentManager;

    @Inject
    public AttachmentChecker(AttachmentManager attachmentManager) {
        this.attachmentManager = attachmentManager;
    }

    public Mono<Void> assertAttachmentsExist(ValueWithId.CreationMessageEntry entry, MailboxSession session) {
        List<Attachment> attachments = entry.getValue().getAttachments();

        if (attachments.isEmpty()) {
            return Mono.empty();
        }
        return Mono.fromRunnable(Throwing.runnable(() -> {
            List<BlobId> notFounds = listAttachmentsNotFound(attachments, session);
            if (!notFounds.isEmpty()) {
                throw new AttachmentsNotFoundException(notFounds);
            }
        })).subscribeOn(Schedulers.elastic())
            .then();
    }

    private List<BlobId> listAttachmentsNotFound(List<Attachment> attachments, MailboxSession session) throws MailboxException {
        ThrowingPredicate<Attachment> notExists =
            attachment -> !attachmentManager.exists(getAttachmentId(attachment), session);
        return attachments.stream()
            .filter(Throwing.predicate(notExists).sneakyThrow())
            .map(Attachment::getBlobId)
            .collect(Guavate.toImmutableList());
    }

    private AttachmentId getAttachmentId(Attachment attachment) {
        return AttachmentId.from(attachment.getBlobId().getRawValue());
    }
}
