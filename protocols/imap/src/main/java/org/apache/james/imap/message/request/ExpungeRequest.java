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

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.request.ImapRequest;

/**
 * {@link ImapRequest} which requests expunge of deleted messages
 */
public class ExpungeRequest extends AbstractImapRequest {
    private final IdRange[] uidRange;

    public ExpungeRequest(Tag tag, IdRange[] uidRange) {
        super(tag, ImapConstants.EXPUNGE_COMMAND);
        this.uidRange = uidRange;
    }

    /**
     * Return an Array of {@link IdRange} to expunge or null if all should get
     * expunged
     * 
     * @return range
     */
    public final IdRange[] getUidSet() {
        return uidRange;
    }

}
