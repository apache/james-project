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

package org.apache.james.mailbox.cassandra.mail.eventsourcing.acl;

import java.util.Objects;

import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ACLCommandDTO {
    public static ACLCommandDTO fromCommand(MailboxACL.ACLCommand command) {
        return new ACLCommandDTO(command.getEditMode().name(),
            command.getEntryKey().serialize(),
            command.getRights().serialize());
    }

    private final String mode;
    private final String entry;
    private final String rights;

    @JsonCreator
    public ACLCommandDTO(@JsonProperty("mode") String mode,
                         @JsonProperty("entry") String entry,
                         @JsonProperty("rights") String rights) {
        this.mode = mode;
        this.entry = entry;
        this.rights = rights;
    }


    @JsonProperty("mode")
    public String getMode() {
        return mode;
    }

    @JsonProperty("entry")
    public String getEntry() {
        return entry;
    }

    @JsonProperty("rights")
    public String getRights() {
        return rights;
    }

    public MailboxACL.ACLCommand asACLCommand() {
        try {
            return MailboxACL.command()
                .key(MailboxACL.EntryKey.deserialize(entry))
                .rights(MailboxACL.Rfc4314Rights.deserialize(rights))
                .mode(MailboxACL.EditMode.parse(mode).orElseThrow(() -> new IllegalArgumentException(mode + " is not a supported EditMode")))
                .build();
        } catch (UnsupportedRightException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ACLCommandDTO) {
            ACLCommandDTO that = (ACLCommandDTO) o;

            return Objects.equals(this.mode, that.mode)
                && Objects.equals(this.entry, that.entry)
                && Objects.equals(this.rights, that.rights);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(mode, entry, rights);
    }
}
