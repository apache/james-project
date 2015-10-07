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
import org.apache.james.imap.api.message.request.ImapRequest;

/**
 * Abstract base class for {@link ImapRequest} implementations
 */
abstract public class AbstractImapRequest implements ImapRequest {

    private final String tag;

    private final ImapCommand command;

    public AbstractImapRequest(final String tag, final ImapCommand command) {
        this.tag = tag;
        this.command = command;
    }

    /**
     * Gets the IMAP command whose execution is requested by the client.
     * 
     * @see org.apache.james.imap.api.message.request.ImapRequest#getCommand()
     */
    public final ImapCommand getCommand() {
        return command;
    }

    /**
     * Gets the prefix tag identifying this request.
     * 
     * @see org.apache.james.imap.api.message.request.ImapRequest#getTag()
     */
    public final String getTag() {
        return tag;
    }

}
