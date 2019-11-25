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

import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.DeleteResult;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;

import com.google.common.collect.ImmutableList;

public interface MessageIdManager {

    Set<MessageId> accessibleMessages(Collection<MessageId> messageIds, final MailboxSession mailboxSession) throws MailboxException;

    void setFlags(Flags newState, FlagsUpdateMode replace, MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException;

    List<MessageResult> getMessages(List<MessageId> messageId, FetchGroup minimal, MailboxSession mailboxSession) throws MailboxException;

    DeleteResult delete(MessageId messageId, List<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException;

    DeleteResult delete(List<MessageId> messageId, MailboxSession mailboxSession) throws MailboxException;

    void setInMailboxes(MessageId messageId, Collection<MailboxId> mailboxIds, MailboxSession mailboxSession) throws MailboxException;

    default DeleteResult delete(MessageId messageId, MailboxSession mailboxSession) throws MailboxException {
        return delete(ImmutableList.of(messageId), mailboxSession);
    }

}
