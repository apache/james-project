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
package org.apache.james.mailbox.inmemory;

import org.apache.james.mailbox.store.mail.model.MailboxId;

public class InMemoryId implements MailboxId {

    public static InMemoryId of(long value) {
        return new InMemoryId(value);
    }

    private final long value;

    private InMemoryId(long value) {
        this.value = value;
    }

    @Override
    public String serialize() {
        return String.valueOf(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    public long getRawId() {
        return value;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (value ^ (value >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        InMemoryId other = (InMemoryId) obj;
        if (value != other.value)
            return false;
        return true;
    }

}
