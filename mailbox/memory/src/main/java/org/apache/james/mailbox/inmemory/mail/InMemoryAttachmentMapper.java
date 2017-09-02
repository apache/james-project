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
package org.apache.james.mailbox.inmemory.mail;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

public class InMemoryAttachmentMapper implements AttachmentMapper {
    
    private static final int INITIAL_SIZE = 128;
    private final Map<AttachmentId, Attachment> attachmentsById;

    public InMemoryAttachmentMapper() {
        attachmentsById = new ConcurrentHashMap<>(INITIAL_SIZE);
    }

    @Override
    public Attachment getAttachment(AttachmentId attachmentId) throws AttachmentNotFoundException {
        Preconditions.checkArgument(attachmentId != null);
        if (!attachmentsById.containsKey(attachmentId)) {
            throw new AttachmentNotFoundException(attachmentId.getId());
        }
        return attachmentsById.get(attachmentId);
    }

    @Override
    public List<Attachment> getAttachments(Collection<AttachmentId> attachmentIds) {
        Preconditions.checkArgument(attachmentIds != null);
        Builder<Attachment> builder = ImmutableList.<Attachment> builder();
        for (AttachmentId attachmentId : attachmentIds) {
            if (attachmentsById.containsKey(attachmentId)) {
                builder.add(attachmentsById.get(attachmentId));
            }
        }
        return builder.build();
    }

    @Override
    public void storeAttachment(Attachment attachment) throws MailboxException {
        attachmentsById.put(attachment.getAttachmentId(), attachment);
    }

    @Override
    public void endRequest() {
        // Do nothing
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public void storeAttachments(Collection<Attachment> attachments) throws MailboxException {
        for (Attachment attachment: attachments) {
            storeAttachment(attachment);
        }
    }
}