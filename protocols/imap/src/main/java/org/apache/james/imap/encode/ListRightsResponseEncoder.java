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

package org.apache.james.imap.encode;

import java.io.IOException;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.encode.base.AbstractChainedImapEncoder;
import org.apache.james.imap.message.response.ListRightsResponse;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;

/**
 * ACL Response Encoder.
 * 
 * @author Peter Palaga
 */
public class ListRightsResponseEncoder extends AbstractChainedImapEncoder {

    public ListRightsResponseEncoder(ImapEncoder next) {
        super(next);
    }

    /** 
     * @see
     * org.apache.james.imap.encode.base.AbstractChainedImapEncoder#doEncode
     * (org.apache.james.imap.api.ImapMessage,
     * org.apache.james.imap.encode.ImapResponseComposer,
     * org.apache.james.imap.api.process.ImapSession)
     */
    protected void doEncode(ImapMessage acceptableMessage, ImapResponseComposer composer, ImapSession session) throws IOException {
        final ListRightsResponse listRightsResponse = (ListRightsResponse) acceptableMessage;
        composer.untagged();
        composer.commandName(ImapConstants.LISTRIGHTS_RESPONSE_NAME);
        
        String mailboxName = listRightsResponse.getMailboxName();
        composer.mailbox(mailboxName == null ? "" : mailboxName);
        
        String identifier = listRightsResponse.getIdentifier();
        composer.quote(identifier);
        
        Rfc4314Rights[] rights = listRightsResponse.getRights();
        
        for (Rfc4314Rights entry : rights) {
            composer.quote(entry.serialize());
       }
        composer.end();
    }

    /** 
     * @see
     * org.apache.james.imap.encode.base.AbstractChainedImapEncoder#isAcceptable
     * (org.apache.james.imap.api.ImapMessage)
     */
    public boolean isAcceptable(ImapMessage message) {
        return message instanceof ListRightsResponse;
    }
}
