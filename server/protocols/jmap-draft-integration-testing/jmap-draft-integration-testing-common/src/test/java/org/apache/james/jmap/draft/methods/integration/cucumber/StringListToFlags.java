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

package org.apache.james.jmap.draft.methods.integration.cucumber;

import java.util.List;

import jakarta.mail.Flags;

import org.apache.james.jmap.draft.model.Keyword;
import org.apache.james.mailbox.FlagsBuilder;

import com.google.common.collect.ImmutableList;

public class StringListToFlags {
    public static Flags fromFlagList(List<String> flagList) {
        ImmutableList<Flags> flags = flagList.stream()
            .map(StringListToFlags::toFlags)
            .collect(ImmutableList.toImmutableList());
        return new FlagsBuilder().add(flags)
            .build();
    }

    private static Flags toFlags(String flag) {
        if (Keyword.ANSWERED.getFlagName().equals(flag)) {
            return new Flags(Flags.Flag.ANSWERED);
        }
        if (Keyword.DELETED.getFlagName().equals(flag)) {
            return new Flags(Flags.Flag.DELETED);
        }
        if (Keyword.DRAFT.getFlagName().equals(flag)) {
            return new Flags(Flags.Flag.DRAFT);
        }
        if (Keyword.RECENT.getFlagName().equals(flag)) {
            return new Flags(Flags.Flag.RECENT);
        }
        if (Keyword.FLAGGED.getFlagName().equals(flag)) {
            return new Flags(Flags.Flag.FLAGGED);
        }
        if (Keyword.SEEN.getFlagName().equals(flag)) {
            return new Flags(Flags.Flag.SEEN);
        }
        return new Flags(flag);
    }
}
