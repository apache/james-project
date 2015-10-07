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

package org.apache.james.mailbox.store;

import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.Flags;

import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.junit.Test;

public class FlagsUpdateCalculatorTest {

    @Test
    public void flagsShouldBeReplacedWhenReplaceIsTrueAndValueIsTrue() {
        FlagsUpdateCalculator flagsUpdateCalculator = new FlagsUpdateCalculator(
            new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.FLAGGED).add("userflag").build(),
            MessageManager.FlagsUpdateMode.REPLACE);
        assertThat(flagsUpdateCalculator.buildNewFlags(new FlagsBuilder().add(Flags.Flag.RECENT, Flags.Flag.FLAGGED).build()))
            .isEqualTo(new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.FLAGGED).add("userflag").build());
    }

    @Test
    public void flagsShouldBeAddedWhenReplaceIsFalseAndValueIsTrue() {
        FlagsUpdateCalculator flagsUpdateCalculator = new FlagsUpdateCalculator(
            new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.FLAGGED).add("userflag").build(),
            MessageManager.FlagsUpdateMode.ADD);
        assertThat(flagsUpdateCalculator.buildNewFlags(new FlagsBuilder().add(Flags.Flag.RECENT, Flags.Flag.FLAGGED).build()))
            .isEqualTo(new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.FLAGGED, Flags.Flag.RECENT).add("userflag").build());
    }

    @Test
    public void flagsShouldBeRemovedWhenReplaceIsFalseAndValueIsFalse() {
        FlagsUpdateCalculator flagsUpdateCalculator = new FlagsUpdateCalculator(
            new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.FLAGGED).add("userflag").build(),
            MessageManager.FlagsUpdateMode.REMOVE);
        assertThat(flagsUpdateCalculator.buildNewFlags(new FlagsBuilder().add(Flags.Flag.RECENT, Flags.Flag.FLAGGED).build()))
            .isEqualTo(new Flags(Flags.Flag.RECENT));
    }

}
