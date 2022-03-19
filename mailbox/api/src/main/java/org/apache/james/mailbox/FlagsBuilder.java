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

package org.apache.james.mailbox;

import java.util.Arrays;
import java.util.List;

import jakarta.mail.Flags;

public class FlagsBuilder {

    public static FlagsBuilder builder() {
        return new FlagsBuilder();
    }

    private final Flags internalFlags;

    public FlagsBuilder() {
        internalFlags = new Flags();
    }

    public FlagsBuilder add(Flags.Flag... flags) {
        for (Flags.Flag flag : flags) {
            internalFlags.add(flag);
        }
        return this;
    }

    public FlagsBuilder add(String... flags) {
        for (String userFlag : flags) {
            internalFlags.add(userFlag);
        }
        return this;
    }

    public FlagsBuilder add(List<Flags> flags) {
        for (Flags flag : flags) {
            internalFlags.add(flag);
        }
        return this;
    }

    public FlagsBuilder add(Flags... flagsArray) {
        add(Arrays.asList(flagsArray));
        return this;
    }

    public FlagsBuilder isAnswered(boolean isAnswered) {
        if (isAnswered) {
            internalFlags.add(Flags.Flag.ANSWERED);
        }
        return this;
    }

    public FlagsBuilder isDraft(boolean isDraft) {
        if (isDraft) {
            internalFlags.add(Flags.Flag.DRAFT);
        }
        return this;
    }

    public FlagsBuilder isDeleted(boolean isDeleted) {
        if (isDeleted) {
            internalFlags.add(Flags.Flag.DELETED);
        }
        return this;
    }

    public FlagsBuilder isFlagged(boolean isFlagged) {
        if (isFlagged) {
            internalFlags.add(Flags.Flag.FLAGGED);
        }
        return this;
    }

    public FlagsBuilder isRecent(boolean isRecent) {
        if (isRecent) {
            internalFlags.add(Flags.Flag.RECENT);
        }
        return this;
    }

    public FlagsBuilder isSeen(boolean isSeen) {
        if (isSeen) {
            internalFlags.add(Flags.Flag.SEEN);
        }
        return this;
    }

    public Flags build() {
        return new Flags(internalFlags);
    }
}
