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

import java.util.Objects;

import org.apache.james.mailbox.MailboxSession;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class Username {
    public static Username fromMailboxSession(MailboxSession mailboxSession) {
        Preconditions.checkNotNull(mailboxSession);
        Preconditions.checkNotNull(mailboxSession.getUser());

        return fromRawValue(mailboxSession.getUser().asString());
    }

    public static Username fromRawValue(String value) {
        return new Username(value);
    }

    private final String value;

    private Username(final String value) {
        Preconditions.checkNotNull(value);

        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public final boolean equals(final Object o) {
        if (o instanceof Username) {
            Username username = (Username) o;

            return Objects.equals(this.value, username.value);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("value", value)
            .toString();
    }
}
