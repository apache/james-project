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

package org.apache.james.mailbox.probe;

import java.io.InputStream;
import java.util.Collection;
import java.util.Date;

import jakarta.mail.Flags;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;

public interface MailboxProbe {

    MailboxId createMailbox(String namespace, String user, String name);

    MailboxId getMailboxId(String namespace, String user, String name);

    Collection<String> listUserMailboxes(String user);

    void deleteMailbox(String namespace, String user, String name);

    ComposedMessageId appendMessage(String username, MailboxPath mailboxPath, InputStream message, Date internalDate,
            boolean isRecent, Flags flags) throws MailboxException;

    Collection<String> listSubscriptions(String user) throws Exception;

    Collection<MessageId> searchMessage(MultimailboxesSearchQuery expression, String user, long limit);

}