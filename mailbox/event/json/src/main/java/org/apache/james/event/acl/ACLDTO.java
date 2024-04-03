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

package org.apache.james.event.acl;

import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.model.MailboxACL;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;

public class ACLDTO {
    public static ACLDTO fromACL(MailboxACL acl) {
        return new ACLDTO(acl.getEntries().entrySet().stream()
            .map(entry -> Pair.of(entry.getKey().serialize(), entry.getValue().serialize()))
            .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue)));
    }

    private final Map<String, String> entries;

    @JsonCreator
    public ACLDTO(@JsonProperty("entries") Map<String, String> entries) {
        this.entries = entries;
    }

    @JsonProperty("entries")
    public Map<String, String> getEntries() {
        return entries;
    }

    public MailboxACL asACL() {
        return new MailboxACL(entries.entrySet().stream()
            .map(Throwing.function(entry -> Pair.of(MailboxACL.EntryKey.deserialize(entry.getKey()),
                MailboxACL.Rfc4314Rights.deserialize(entry.getValue()))))
            .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue)));
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ACLDTO) {
            ACLDTO that = (ACLDTO) o;

            return Objects.equals(this.entries, that.entries);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(entries);
    }
}
