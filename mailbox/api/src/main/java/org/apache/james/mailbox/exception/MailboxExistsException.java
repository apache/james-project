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


/**
 * Indicates that the operation failed since the mailbox already exists.
 */
public class MailboxExistsException extends MailboxException {

    private static final long serialVersionUID = -486951759505030166L;

    private final String mailboxName;

    public MailboxExistsException(String mailboxName) {
        super("Mailbox with name=" + mailboxName + " already exists.");
        this.mailboxName = mailboxName;
    }

    /**
     * Gets the name of the mailbox which already exists.
     * 
     * @return the mailboxName, not null
     */
    public final String getMailboxName() {
        return mailboxName;
    }

    public String toString() {
        return getMessage();
    }

}
