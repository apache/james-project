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

package org.apache.james.mailbox.indexer.events;

import org.apache.james.mailbox.model.MailboxPath;

import javax.mail.Flags;

public class FlagsMessageEvent implements ImpactingMessageEvent {

    private final MailboxPath mailboxPath;
    private final long uid;
    private final Flags flags;

    public FlagsMessageEvent(MailboxPath mailboxPath, long uid, Flags flags) {
        this.mailboxPath = mailboxPath;
        this.uid = uid;
        this.flags = flags;
    }

    @Override
    public long getUid() {
        return uid;
    }

    @Override
    public MailboxPath getMailboxPath() {
        return mailboxPath;
    }

    @Override
    public ImpactingEventType getType() {
        return ImpactingEventType.FlagsUpdate;
    }

    public Flags getFlags() {
        return flags;
    }
}
