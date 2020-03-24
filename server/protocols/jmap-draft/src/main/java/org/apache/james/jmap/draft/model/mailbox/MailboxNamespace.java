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

package org.apache.james.jmap.draft.model.mailbox;

import java.util.Objects;
import java.util.Optional;

import org.apache.james.core.Username;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class MailboxNamespace {
    public enum Type {
        Delegated("Delegated"),
        Personal("Personal");

        @SuppressWarnings("unused")
        private final String type;

        Type(String type) {
            this.type = type;
        }
    }

    public static MailboxNamespace delegated(Username owner) {
        Preconditions.checkArgument(owner != null);

        return new MailboxNamespace(Type.Delegated, Optional.of(owner));
    }

    public static MailboxNamespace personal() {
        return new MailboxNamespace(Type.Personal, Optional.empty());
    }

    private final Type type;
    private final Optional<Username> owner;

    private MailboxNamespace(Type type, Optional<Username> owner) {
        this.type = type;
        this.owner = owner;
    }

    public Type getType() {
        return type;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Username> getOwner() {
        return owner;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailboxNamespace) {
            MailboxNamespace that = (MailboxNamespace) o;

            return Objects.equals(this.type, that.type) &&
                Objects.equals(this.owner, that.owner);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(type, owner);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("type", type)
            .add("owner", owner)
            .toString();
    }
}
