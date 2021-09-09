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

package org.apache.james.mailbox;

import java.security.SecureRandom;
import java.util.Optional;

import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.UidProvider;

public class RandomUidProvider implements UidProvider {
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public MessageUid nextUid(Mailbox mailbox) {
        return MessageUid.of(Math.abs(secureRandom.nextLong()));
    }

    @Override
    public Optional<MessageUid> lastUid(Mailbox mailbox) {
        return Optional.of(MessageUid.of(Math.abs(secureRandom.nextLong())));
    }

    @Override
    public MessageUid nextUid(MailboxId mailboxId) {
        return MessageUid.of(Math.abs(secureRandom.nextLong()));
    }
}
