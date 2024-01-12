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

package org.apache.james.mailbox.store.mail.model;

import java.util.List;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;

public interface MapperProvider {
    enum Capabilities {
        MESSAGE,
        MAILBOX,
        ATTACHMENT,
        ANNOTATION,
        MOVE,
        UNIQUE_MESSAGE_ID,
        THREAD_SAFE_FLAGS_UPDATE,
        INCREMENTAL_APPLICABLE_FLAGS,
        ACL_STORAGE
    }

    List<Capabilities> getSupportedCapabilities();

    MailboxMapper createMailboxMapper() throws MailboxException;

    MessageMapper createMessageMapper() throws MailboxException;

    MessageIdMapper createMessageIdMapper() throws MailboxException;

    AttachmentMapper createAttachmentMapper() throws MailboxException;

    MailboxId generateId();

    MessageUid generateMessageUid(Mailbox mailbox);

    ModSeq generateModSeq(Mailbox mailbox) throws MailboxException;

    ModSeq highestModSeq(Mailbox mailbox) throws MailboxException;

    boolean supportPartialAttachmentFetch();
    
    MessageId generateMessageId();
}
