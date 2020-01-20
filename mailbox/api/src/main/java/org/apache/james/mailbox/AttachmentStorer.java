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

import java.util.List;

import javax.mail.internet.SharedInputStream;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;

import com.google.common.collect.ImmutableList;

public interface AttachmentStorer {
    /**
     * If applicable, this method will parse the messageContent to retrieve associated attachments and will store them.
     */
    List<MessageAttachment> storeAttachments(MessageId messageId, SharedInputStream messageContent, MailboxSession session) throws MailboxException;

    class NoopAttachmentStorer implements AttachmentStorer {
        @Override
        public List<MessageAttachment> storeAttachments(MessageId messageId, SharedInputStream messageContent, MailboxSession session) {
            return ImmutableList.of();
        }
    }
}
