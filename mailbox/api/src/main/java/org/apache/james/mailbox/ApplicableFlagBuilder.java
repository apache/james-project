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

import java.util.List;

import jakarta.mail.Flags;

import com.google.common.annotations.VisibleForTesting;

public class ApplicableFlagBuilder {

    @VisibleForTesting static final Flags DEFAULT_APPLICABLE_FLAGS = FlagsBuilder.builder()
            .add(Flags.Flag.ANSWERED,
                    Flags.Flag.DELETED,
                    Flags.Flag.DRAFT,
                    Flags.Flag.FLAGGED,
                    Flags.Flag.SEEN)
            .build();

    public static ApplicableFlagBuilder builder() {
        return new ApplicableFlagBuilder();
    }

    public static ApplicableFlagBuilder from(Flags... flags) {
        return new ApplicableFlagBuilder()
                .add(flags);
    }

    public static ApplicableFlagBuilder from(String... flags) {
        return new ApplicableFlagBuilder()
                .add(flags);
    }

    private final FlagsBuilder builder;

    private ApplicableFlagBuilder() {
        builder = FlagsBuilder.builder().add(DEFAULT_APPLICABLE_FLAGS);
    }

    public ApplicableFlagBuilder add(String... flags) {
        builder.add(flags);
        return this;
    }

    public ApplicableFlagBuilder add(Flags... flags) {
        builder.add(flags);
        return this;
    }

    public ApplicableFlagBuilder add(List<Flags> flags) {
        builder.add(flags);
        return this;
    }

    public Flags build() {
        Flags flags = builder.build();
        flags.remove(Flags.Flag.RECENT);
        flags.remove(Flags.Flag.USER);
        return flags;
    }
}
