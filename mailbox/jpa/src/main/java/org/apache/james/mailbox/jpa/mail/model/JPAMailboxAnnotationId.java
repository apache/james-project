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

package org.apache.james.mailbox.jpa.mail.model;

import java.io.Serializable;

import javax.persistence.Embeddable;

import com.google.common.base.Objects;

@Embeddable
public final class JPAMailboxAnnotationId implements Serializable {
    private long mailboxId;
    private String key;

    public JPAMailboxAnnotationId(long mailboxId, String key) {
        this.mailboxId = mailboxId;
        this.key = key;
    }

    public JPAMailboxAnnotationId() {
    }

    public long getMailboxId() {
        return mailboxId;
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof JPAMailboxAnnotationId) {
            JPAMailboxAnnotationId that = (JPAMailboxAnnotationId) o;
            return Objects.equal(this.mailboxId, that.mailboxId) && Objects.equal(this.key, that.key);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mailboxId, key);
    }
}
