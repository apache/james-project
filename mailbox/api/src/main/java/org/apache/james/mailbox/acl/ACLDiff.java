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
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.james.mailbox.model.MailboxACL;

public class ACLDiff {

    public static ACLDiff computeDiff(MailboxACL oldACL, MailboxACL newACL) {
        return new ACLDiff(oldACL, newACL);
    }

    private final MailboxACL oldACL;
    private final MailboxACL newACL;

    public ACLDiff(MailboxACL oldACL, MailboxACL newACL) {
        this.oldACL = oldACL;
        this.newACL = newACL;
    }

    public Stream<MailboxACL.Entry> addedEntries() {
        return newACL.getEntries()
            .entrySet()
            .stream()
            .filter(entry -> !oldACL.getEntries().containsKey(entry.getKey()))
            .map(entry -> new MailboxACL.Entry(entry.getKey(), entry.getValue()));
    }

    public Stream<MailboxACL.Entry> removedEntries() {
        return oldACL.getEntries()
            .entrySet()
            .stream()
            .filter(entry -> !newACL.getEntries().containsKey(entry.getKey()))
            .map(entry -> new MailboxACL.Entry(entry.getKey(), entry.getValue()));
    }

    public Stream<MailboxACL.Entry> changedEntries() {
        Map<MailboxACL.EntryKey, MailboxACL.Rfc4314Rights> oldEntries = oldACL.getEntries();

        return newACL.getEntries()
            .entrySet()
            .stream()
            .filter(entry -> hasKeyWithDifferentValue(oldEntries, entry))
            .map(entry -> new MailboxACL.Entry(entry.getKey(), entry.getValue()));
    }

    private boolean hasKeyWithDifferentValue(Map<MailboxACL.EntryKey, MailboxACL.Rfc4314Rights> oldEntries,
                                             Map.Entry<MailboxACL.EntryKey, MailboxACL.Rfc4314Rights> entry) {
        return oldEntries.containsKey(entry.getKey())
            && !oldEntries.get(entry.getKey()).equals(entry.getValue());
    }

    public Stream<MailboxACL.ACLCommand> commands() {
        return Stream.concat(
            addedEntries()
                .map(entry -> MailboxACL.command()
                    .mode(MailboxACL.EditMode.ADD)
                    .key(entry.getKey())
                    .rights(entry.getValue())
                    .build()),
            removedEntries()
                .map(entry -> MailboxACL.command()
                    .mode(MailboxACL.EditMode.REMOVE)
                    .key(entry.getKey())
                    .rights(entry.getValue())
                    .build()));
    }

    public MailboxACL getOldACL() {
        return oldACL;
    }

    public MailboxACL getNewACL() {
        return newACL;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ACLDiff) {
            ACLDiff aclDiff = (ACLDiff) o;

            return Objects.equals(this.oldACL, aclDiff.oldACL)
                && Objects.equals(this.newACL, aclDiff.newACL);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(oldACL, newACL);
    }
}
