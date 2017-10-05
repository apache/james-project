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

import java.util.Map;
import java.util.stream.Stream;

import org.apache.james.mailbox.model.MailboxACL;

public class ACLDiff {

    public static ACLDiff from(MailboxACL oldACL, MailboxACL newACL) {
        return new ACLDiff(oldACL, newACL);
    }

    private final MailboxACL oldACL;
    private final MailboxACL newACL;

    private ACLDiff(MailboxACL oldACL, MailboxACL newACL) {
        this.oldACL = oldACL;
        this.newACL = newACL;
    }

    public Stream<MailboxACL.Entry> addedEntries() {
        Map<MailboxACL.EntryKey, MailboxACL.Rfc4314Rights> oldEntries = oldACL.ofPositiveNameType(MailboxACL.NameType.user);

        return newACL.ofPositiveNameType(MailboxACL.NameType.user)
            .entrySet()
            .stream()
            .filter(entry -> !oldEntries.containsKey(entry.getKey()))
            .map(entry -> new MailboxACL.Entry(entry.getKey(), entry.getValue()));
    }

    public Stream<MailboxACL.Entry> removedEntries() {
        Map<MailboxACL.EntryKey, MailboxACL.Rfc4314Rights> newEntries = newACL.ofPositiveNameType(MailboxACL.NameType.user);

        return oldACL.ofPositiveNameType(MailboxACL.NameType.user)
            .entrySet()
            .stream()
            .filter(entry -> !newEntries.containsKey(entry.getKey()))
            .map(entry -> new MailboxACL.Entry(entry.getKey(), entry.getValue()));
    }

    public Stream<MailboxACL.Entry> changedEntries() {
        Map<MailboxACL.EntryKey, MailboxACL.Rfc4314Rights> oldEntries = oldACL.ofPositiveNameType(MailboxACL.NameType.user);

        return newACL.ofPositiveNameType(MailboxACL.NameType.user)
            .entrySet()
            .stream()
            .filter(entry -> oldEntries.containsKey(entry.getKey())
                && !oldEntries.get(entry.getKey()).equals(entry.getValue()))
            .map(entry -> new MailboxACL.Entry(entry.getKey(), entry.getValue()));
    }
}
