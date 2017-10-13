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

package org.apache.james.jmap.methods.integration.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.jmap.model.Keyword;
import org.junit.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

public class StringListToFlagsTest {
    @Test
    public void fromFlagListShouldConvertAnwseredFlag() throws Exception {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of(Keyword.ANSWERED.getFlagName())))
            .isEqualTo(new Flags(Flag.ANSWERED));
    }

    @Test
    public void fromFlagListShouldConvertSeenFlag() throws Exception {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of(Keyword.SEEN.getFlagName())))
            .isEqualTo(new Flags(Flag.SEEN));
    }

    @Test
    public void fromFlagListShouldConvertDraftFlag() throws Exception {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of(Keyword.DRAFT.getFlagName())))
            .isEqualTo(new Flags(Flag.DRAFT));
    }

    @Test
    public void fromFlagListShouldConvertRecentFlag() throws Exception {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of(Keyword.RECENT.getFlagName())))
            .isEqualTo(new Flags(Flag.RECENT));
    }

    @Test
    public void fromFlagListShouldConvertDeletedFlag() throws Exception {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of(Keyword.DELETED.getFlagName())))
            .isEqualTo(new Flags(Flag.DELETED));
    }

    @Test
    public void fromFlagListShouldConvertFlaggedFlag() throws Exception {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of(Keyword.FLAGGED.getFlagName())))
            .isEqualTo(new Flags(Flag.FLAGGED));
    }

    @Test
    public void fromFlagListShouldConvertValidJMAPFlag() throws Exception {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of("$Any")))
            .isEqualTo(new Flags("$Any"));
    }

    @Test
    public void fromFlagListShouldConvertInvalidJMAPFlag() throws Exception {
        assertThat(StringListToFlags.fromFlagList(ImmutableList.of("op§")))
            .isEqualTo(new Flags("op§"));
    }

}