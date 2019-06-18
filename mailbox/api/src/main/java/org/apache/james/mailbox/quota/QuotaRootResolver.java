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

package org.apache.james.mailbox.quota;

import java.util.List;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;

public interface QuotaRootResolver {

    /**
     * Return the quotaRoot associated with the given mailbox name.
     *
     * @param mailboxPath The name of the mailbox
     * @return QuotaRoot ruling this mailbox ( we uses user owning this mailbox name )
     * @throws MailboxException
     */
    QuotaRoot getQuotaRoot(MailboxPath mailboxPath) throws MailboxException;

    QuotaRoot getQuotaRoot(MailboxId mailboxId) throws MailboxException;

    QuotaRoot fromString(String serializedQuotaRoot) throws MailboxException;

    List<MailboxPath> retrieveAssociatedMailboxes(QuotaRoot quotaRoot, MailboxSession mailboxSession) throws MailboxException;
}
