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
package org.apache.james.mailbox.store.mail.model;

import javax.mail.Flags;

public class FlagsBuilder {

    public static Flags createFlags(MailboxMessage<?> mailboxMessage, String[] userFlags) {
        final Flags flags = new Flags();

        if (mailboxMessage.isAnswered()) {
            flags.add(Flags.Flag.ANSWERED);
        }
        if (mailboxMessage.isDeleted()) {
            flags.add(Flags.Flag.DELETED);
        }
        if (mailboxMessage.isDraft()) {
            flags.add(Flags.Flag.DRAFT);
        }
        if (mailboxMessage.isFlagged()) {
            flags.add(Flags.Flag.FLAGGED);
        }
        if (mailboxMessage.isRecent()) {
            flags.add(Flags.Flag.RECENT);
        }
        if (mailboxMessage.isSeen()) {
            flags.add(Flags.Flag.SEEN);
        }
        if (userFlags != null && userFlags.length > 0) {
            for (int i = 0; i < userFlags.length; i++) {
                flags.add(userFlags[i]);
            }
        }
        return flags;
    }


}
