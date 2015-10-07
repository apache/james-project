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

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.store.mail.model.Mailbox;

public class GroupFolderResolver {
    
    private MailboxSession mailboxSession;

    public GroupFolderResolver(MailboxSession mailboxSession) {
        this.mailboxSession = mailboxSession;
    }

    public boolean isGroupFolder(Mailbox<?> mailbox) {
        String namespace = mailbox.getNamespace();
        return namespace == null || 
                (!namespace.equals(mailboxSession.getPersonalSpace())
                && !namespace.equals(MailboxConstants.USER_NAMESPACE)
                && !namespace.equals(mailboxSession.getOtherUsersSpace()));
    }
}
