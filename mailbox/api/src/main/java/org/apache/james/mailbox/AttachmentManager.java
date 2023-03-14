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

package org.apache.james.mailbox;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;

import reactor.core.publisher.Mono;

public interface AttachmentManager extends AttachmentContentLoader {

    boolean exists(AttachmentId attachmentId, MailboxSession session) throws MailboxException;

    AttachmentMetadata getAttachment(AttachmentId attachmentId, MailboxSession mailboxSession) throws MailboxException, AttachmentNotFoundException;

    List<AttachmentMetadata> getAttachments(List<AttachmentId> attachmentIds, MailboxSession mailboxSession) throws MailboxException;

    InputStream loadAttachmentContent(AttachmentId attachmentId, MailboxSession mailboxSession) throws AttachmentNotFoundException, IOException;

    Mono<InputStream> loadAttachmentContentReactive(AttachmentId attachmentId, MailboxSession mailboxSession);

    @Override
    default InputStream load(AttachmentMetadata attachment, MailboxSession mailboxSession) throws IOException, AttachmentNotFoundException {
        return loadAttachmentContent(attachment.getAttachmentId(), mailboxSession);
    }

    @Override
    default Mono<InputStream> loadReactive(AttachmentMetadata attachment, MailboxSession mailboxSession) {
        return loadAttachmentContentReactive(attachment.getAttachmentId(), mailboxSession);
    }

}
