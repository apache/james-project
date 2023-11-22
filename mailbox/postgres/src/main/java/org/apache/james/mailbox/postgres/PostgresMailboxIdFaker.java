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

package org.apache.james.mailbox.postgres;

import java.util.UUID;

import org.apache.james.mailbox.model.MailboxId;

// TODO remove: this is trick convert JPAId to PostgresMailboxId when implementing PostgresUidProvider.
// it should be removed when all JPA dependencies are removed
@Deprecated
public class PostgresMailboxIdFaker {
    public static PostgresMailboxId getMailboxId(MailboxId mailboxId) {
        if (mailboxId instanceof JPAId) {
            long longValue = ((JPAId) mailboxId).getRawId();
            return PostgresMailboxId.of(longToUUID(longValue));
        }
        return (PostgresMailboxId) mailboxId;
    }

    public static UUID longToUUID(Long longValue) {
        long mostSigBits = longValue << 32;
        long leastSigBits = 0;
        return new UUID(mostSigBits, leastSigBits);
    }
}
