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
package org.apache.james.mailbox.store.mail;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.reactivestreams.Publisher;

public interface AttachmentMapper extends Mapper {

    InputStream loadAttachmentContent(AttachmentId attachmentId) throws AttachmentNotFoundException, IOException;

    Attachment getAttachment(AttachmentId attachmentId) throws AttachmentNotFoundException;

    List<Attachment> getAttachments(Collection<AttachmentId> attachmentIds);

    Publisher<Void> storeAttachmentForOwner(Attachment attachment, Username owner);

    void storeAttachmentsForMessage(Collection<Attachment> attachments, MessageId ownerMessageId) throws MailboxException;

    Collection<MessageId> getRelatedMessageIds(AttachmentId attachmentId) throws MailboxException;

    Collection<Username> getOwners(AttachmentId attachmentId) throws MailboxException;
}