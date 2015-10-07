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

package org.apache.james.imap.processor.base;

import java.util.List;

import org.apache.james.mailbox.MailboxListener.FlagsUpdated;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UpdatedFlags;

public class FakeMailboxListenerFlagsUpdate extends FlagsUpdated {

    public List<Long> uids;

    public List<UpdatedFlags> flags;

    public FakeMailboxListenerFlagsUpdate(MailboxSession session, List<Long> uids, List<UpdatedFlags> flags, MailboxPath path) {
        super(session, path);
        this.uids = uids;
        this.flags = flags;
    }

    /**
     * @see org.apache.james.mailbox.MailboxListener.FlagsUpdated#getUpdatedFlags()
     */
    public List<UpdatedFlags> getUpdatedFlags() {
        return flags;
    }

    /**
     * @see org.apache.james.mailbox.MailboxListener.MessageEvent#getUids()
     */
    public List<Long> getUids() {
        return uids;
    }
}
