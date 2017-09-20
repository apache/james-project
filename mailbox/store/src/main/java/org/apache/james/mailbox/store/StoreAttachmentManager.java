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

package org.apache.james.mailbox.store;

import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;

public class StoreAttachmentManager implements AttachmentManager {

    private final AttachmentMapperFactory attachmentMapperFactory;

    @Inject
    public StoreAttachmentManager(AttachmentMapperFactory attachmentMapperFactory) {
        this.attachmentMapperFactory = attachmentMapperFactory;
    }

    protected AttachmentMapperFactory getAttachmentMapperFactory() {
        return attachmentMapperFactory;
    }

    protected AttachmentMapper getAttachmentMapper(MailboxSession mailboxSession) throws MailboxException {
        return attachmentMapperFactory.getAttachmentMapper(mailboxSession);
    }

    @Override
    public Attachment getAttachment(AttachmentId attachmentId, MailboxSession mailboxSession) throws MailboxException, AttachmentNotFoundException {
        return getAttachmentMapper(mailboxSession).getAttachment(attachmentId);
    }

    @Override
    public List<Attachment> getAttachments(List<AttachmentId> attachmentIds, MailboxSession mailboxSession) throws MailboxException {
        return getAttachmentMapper(mailboxSession).getAttachments(attachmentIds);
    }

    @Override
    public void storeAttachment(Attachment attachment, MailboxSession mailboxSession) throws MailboxException {
        getAttachmentMapper(mailboxSession).storeAttachment(attachment);
    }

    @Override
    public void storeAttachmentsForMessage(Collection<Attachment> attachments, MessageId ownerMessageId, MailboxSession mailboxSession) throws MailboxException {
        getAttachmentMapper(mailboxSession).storeAttachmentsForMessage(attachments, ownerMessageId);
    }

    @Override
    public Collection<MessageId> getRelatedMessageIds(AttachmentId attachmentId, MailboxSession mailboxSession) throws MailboxException {
        return getAttachmentMapper(mailboxSession).getRelatedMessageIds(attachmentId);
    }
}
