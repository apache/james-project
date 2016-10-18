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

package org.apache.james.mailbox.model;

import org.apache.james.mailbox.MessageUid;

public class ComposedMessageId {

    private final MailboxId mailboxId;
    private final MessageId messageId;
    private final MessageUid uid;

    public ComposedMessageId(MailboxId mailboxId, MessageId messageId, MessageUid uid) {
        this.mailboxId = mailboxId;
        this.messageId = messageId;
        this.uid = uid;
    }

    public MailboxId getMailboxId() {
        return mailboxId;
    }

    public MessageId getMessageId() {
        return messageId;
    }

    public MessageUid getUid() {
        return uid;
    }

}
