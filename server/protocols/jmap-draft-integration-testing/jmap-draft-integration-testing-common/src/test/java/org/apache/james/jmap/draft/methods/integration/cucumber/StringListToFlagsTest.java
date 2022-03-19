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

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;

import org.apache.james.jmap.draft.model.Keyword;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class StringListToFlagsTest {
    @Test
    public void fromFlagListShouldConvertAnwseredFlag() {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of(Keyword.ANSWERED.getFlagName())))
            .isEqualTo(new Flags(Flag.ANSWERED));
    }

    @Test
    public void fromFlagListShouldConvertSeenFlag() {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of(Keyword.SEEN.getFlagName())))
            .isEqualTo(new Flags(Flag.SEEN));
    }

    @Test
    public void fromFlagListShouldConvertDraftFlag() {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of(Keyword.DRAFT.getFlagName())))
            .isEqualTo(new Flags(Flag.DRAFT));
    }

    @Test
    public void fromFlagListShouldConvertRecentFlag() {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of(Keyword.RECENT.getFlagName())))
            .isEqualTo(new Flags(Flag.RECENT));
    }

    @Test
    public void fromFlagListShouldConvertDeletedFlag() {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of(Keyword.DELETED.getFlagName())))
            .isEqualTo(new Flags(Flag.DELETED));
    }

    @Test
    public void fromFlagListShouldConvertFlaggedFlag() {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of(Keyword.FLAGGED.getFlagName())))
            .isEqualTo(new Flags(Flag.FLAGGED));
    }

    @Test
    public void fromFlagListShouldConvertValidJMAPFlag() {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of("$Any")))
            .isEqualTo(new Flags("$Any"));
    }

    @Test
    public void fromFlagListShouldConvertInvalidJMAPFlag() {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of("op§")))
            .isEqualTo(new Flags("op§"));
    }

}