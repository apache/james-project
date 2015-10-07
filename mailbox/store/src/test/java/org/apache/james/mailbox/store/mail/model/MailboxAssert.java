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

import org.assertj.core.api.AbstractAssert;

public class MailboxAssert extends AbstractAssert<MailboxAssert, Mailbox<?>> {
    public MailboxAssert(Mailbox<?> actual) {
        super(actual, MailboxAssert.class);
    }

    public static MailboxAssert assertThat(Mailbox<?> actual) {
        return new MailboxAssert(actual);
    }

    public MailboxAssert isEqualTo(Mailbox<?> expected) {
        isNotNull();
        if (!equals(actual.getMailboxId(), expected.getMailboxId())) {
            failWithMessage("Expected UUID to be <%s> but was <%s>", expected.getMailboxId(), actual.getMailboxId());
        }
        if (!equals(actual.getNamespace(), expected.getNamespace())) {
            failWithMessage("Expected NameSpace to be <%s> but was <%s>", expected.getNamespace(), actual.getNamespace());
        }
        if (!equals(actual.getUser(), expected.getUser())) {
            failWithMessage("Expected User to be <%s> but was <%s>", expected.getUser(), actual.getUser());
        }
        if (!equals(actual.getName(), expected.getName())) {
            failWithMessage("Expected Name to be <%s> but was <%s>", expected.getName(), actual.getName());
        }
        if (!equals(actual.getACL(), expected.getACL())) {
            failWithMessage("Expected UUID to be <%s> but was <%s>", expected.getACL(), actual.getACL());
        }
        if (actual.getUidValidity() != expected.getUidValidity()) {
            failWithMessage("Expected UID Validity to be <%s> but was <%s>", expected.getUidValidity(), actual.getUidValidity());
        }
        return this;
    }

    private boolean equals(Object object1, Object object2) {
        if ( object1 == null && object2 == null ) {
            return true;
        }
        return ( object1 != null ) && object1.equals(object2);
    }
}
