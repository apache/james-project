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

package org.apache.james.vault.memory;

import static org.apache.james.vault.DeletedMessageFixture.NOW;

import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.DeletedMessageVaultContract;
import org.apache.james.vault.DeletedMessageVaultSearchContract;
import org.apache.james.vault.RetentionConfiguration;
import org.junit.jupiter.api.BeforeEach;

public class MemoryDeletedMessagesVaultTest implements DeletedMessageVaultContract, DeletedMessageVaultSearchContract.AllContracts {

    private MemoryDeletedMessagesVault memoryDeletedMessagesVault;

    @BeforeEach
    void setUp() {
        memoryDeletedMessagesVault = new MemoryDeletedMessagesVault(RetentionConfiguration.DEFAULT, CLOCK);
    }

    @Override
    public DeletedMessageVault getVault() {
        return memoryDeletedMessagesVault;
    }

    @Override
    public UpdatableTickingClock getClock() {
        return new UpdatableTickingClock(NOW.toInstant());
    }
}
