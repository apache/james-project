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

package org.apache.james.imap.message.request;

import org.apache.james.imap.api.ImapCommand;

/**
 * SETACL Request.
 * 
 * @author Peter Palaga
 */
public class SetACLRequest extends AbstractImapRequest {
    private final String identifier;
    private final String mailboxName;
    private final String rights;

    public SetACLRequest(String tag, ImapCommand command, String mailboxName, String identifier, String rights) {
        super(tag, command);
        this.mailboxName = mailboxName;
        this.identifier = identifier;
        this.rights = rights;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getMailboxName() {
        return mailboxName;
    }

    public String getRights() {
        return rights;
    }

}
