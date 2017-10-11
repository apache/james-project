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

package org.apache.james.mailbox.exception;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;

/**
 * Indicates that the failure is caused by a reference to a mailbox which does
 * not exist.
 */
public class MailboxNotFoundException extends MailboxException {

    private static final long serialVersionUID = -8493370806722264915L;

    /**
     * @param mailboxName
     *            name of the mailbox, not null
     */
    public MailboxNotFoundException(String message) {
        super(message);
    }

    public MailboxNotFoundException(MailboxId mailboxId) {
        super(mailboxId.serialize() + " can not be found");
    }

    /**
     * @param mailboxPath
     *            name of the mailbox, not null
     */
    public MailboxNotFoundException(MailboxPath mailboxPath) {
        super(mailboxPath + " can not be found");
    }

}
