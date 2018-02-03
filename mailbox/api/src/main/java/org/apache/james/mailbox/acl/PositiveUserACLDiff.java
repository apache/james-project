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
package org.apache.james.mailbox.acl;

import java.util.stream.Stream;

import org.apache.james.mailbox.model.MailboxACL;

public class PositiveUserACLDiff {
    private final ACLDiff aclDiff;

    public PositiveUserACLDiff(ACLDiff aclDiff) {
        this.aclDiff = aclDiff;
    }

    public static PositiveUserACLDiff computeDiff(MailboxACL oldACL, MailboxACL newACL) {
        return new PositiveUserACLDiff(ACLDiff.computeDiff(oldACL, newACL));
    }

    public Stream<MailboxACL.Entry> addedEntries() {
        return aclDiff.addedEntries()
            .filter(this::hasPositiveUserKey);
    }

    public Stream<MailboxACL.Entry> removedEntries() {
        return aclDiff.removedEntries()
            .filter(this::hasPositiveUserKey);
    }

    public Stream<MailboxACL.Entry> changedEntries() {
        return aclDiff.changedEntries()
            .filter(this::hasPositiveUserKey);
    }

    private boolean hasPositiveUserKey(MailboxACL.Entry entry) {
        return !entry.getKey().isNegative()
            && entry.getKey().getNameType().equals(MailboxACL.NameType.user);
    }
}
