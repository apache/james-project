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

package org.apache.james.dlp.eventsourcing.commands;

import java.util.Objects;

import org.apache.james.core.Domain;
import org.apache.james.eventsourcing.Command;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class ClearCommand implements Command {
    private final Domain domain;

    public ClearCommand(Domain domain) {
        Preconditions.checkNotNull(domain);

        this.domain = domain;
    }

    public Domain getDomain() {
        return domain;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ClearCommand) {
            ClearCommand that = (ClearCommand) o;

            return Objects.equals(this.domain, that.domain);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(domain);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("domain", domain)
            .toString();
    }
}
