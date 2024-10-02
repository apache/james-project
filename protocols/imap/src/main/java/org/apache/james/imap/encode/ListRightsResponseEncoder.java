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
import java.util.List;
import java.util.Optional;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.message.response.ListRightsResponse;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;

import com.github.fge.lambdas.Throwing;

/**
 * ACL Response Encoder.
 */
public class ListRightsResponseEncoder implements ImapResponseEncoder<ListRightsResponse> {
    @Override
    public Class<ListRightsResponse> acceptableMessages() {
        return ListRightsResponse.class;
    }

    @Override
    public void encode(ListRightsResponse listRightsResponse, ImapResponseComposer composer) throws IOException {
        composer.untagged();
        composer.commandName(ImapConstants.LISTRIGHTS_COMMAND);

        Optional.ofNullable(listRightsResponse.getMailboxName()).ifPresent(Throwing.consumer(value -> composer.mailbox(value.asString())));

        MailboxACL.EntryKey entryKey = listRightsResponse.getEntryKey();
        composer.quote(entryKey.toString());
        
        List<Rfc4314Rights> rights = listRightsResponse.getRights();
        
        for (Rfc4314Rights entry : rights) {
            composer.quote(entry.serialize());
       }
        composer.end();
    }
}
