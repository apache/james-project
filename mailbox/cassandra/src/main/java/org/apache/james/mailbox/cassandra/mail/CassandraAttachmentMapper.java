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

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.util.FluentFutureStream;
import org.apache.james.util.OptionalUtils;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.ThrownByLambdaException;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class CassandraAttachmentMapper implements AttachmentMapper {

    private static final boolean LOG_IF_EMPTY = true;

    private final CassandraAttachmentDAO attachmentDAO;

    @Inject
    public CassandraAttachmentMapper(CassandraAttachmentDAO attachmentDAO) {
        this.attachmentDAO = attachmentDAO;
    }

    @Override
    public void endRequest() {
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public Attachment getAttachment(AttachmentId attachmentId) throws AttachmentNotFoundException {
        Preconditions.checkArgument(attachmentId != null);
        return attachmentDAO.getAttachment(attachmentId)
            .join()
            .orElseThrow(() -> new AttachmentNotFoundException(attachmentId.getId()));
    }

    @Override
    public List<Attachment> getAttachments(Collection<AttachmentId> attachmentIds) {
        return getAttachmentsAsFuture(attachmentIds).join();
    }

    public CompletableFuture<ImmutableList<Attachment>> getAttachmentsAsFuture(Collection<AttachmentId> attachmentIds) {
        Preconditions.checkArgument(attachmentIds != null);

        Stream<CompletableFuture<Optional<Attachment>>> attachments = attachmentIds
                .stream()
                .distinct()
                .map(id -> attachmentDAO.getAttachment(id, LOG_IF_EMPTY));

        return FluentFutureStream
            .of(attachments)
            .flatMap(OptionalUtils::toStream)
            .collect(Guavate.toImmutableList());
    }

    @Override
    public void storeAttachment(Attachment attachment) throws MailboxException {
        try {
            attachmentDAO.storeAttachment(attachment).join();
        } catch (IOException e) {
            throw new MailboxException(e.getMessage(), e);
        }
    }

    @Override
    public void storeAttachments(Collection<Attachment> attachments) throws MailboxException {
        try {
            FluentFutureStream.of(
                attachments.stream()
                    .map(Throwing.function(attachmentDAO::storeAttachment)))
                .join();
        } catch (ThrownByLambdaException e) {
            throw new MailboxException(e.getCause().getMessage(), e.getCause());
        }
    }
}
