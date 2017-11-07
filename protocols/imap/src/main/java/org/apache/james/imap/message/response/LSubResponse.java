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
package org.apache.james.imap.message.response;

import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.mailbox.model.MailboxMetaData;

/**
 * Values an IMAP4rev1 <code>LIST</code> response.
 */
public final class LSubResponse extends AbstractListingResponse implements ImapResponseMessage {
    public LSubResponse(String name, boolean noSelect, char delimiter) {
        super(MailboxMetaData.Children.CHILDREN_ALLOWED_BUT_UNKNOWN, fromNoSelect(noSelect), name, delimiter, MailboxType.OTHER);
    }

    private static MailboxMetaData.Selectability fromNoSelect(boolean noSelect) {
        if (noSelect) {
            return MailboxMetaData.Selectability.NOSELECT;
        }
        return MailboxMetaData.Selectability.NONE;
    }
}
