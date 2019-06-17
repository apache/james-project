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

package org.apache.james.backends.cassandra.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

public class MigrationTest {
    @Test
    public void combineShouldNotExecuteSecondMigrationExecutionWhenTheFirstOneIsFailing() {
        AtomicBoolean migration2Done = new AtomicBoolean(false);

        Migration migration1 = () -> Migration.Result.PARTIAL;
        Migration migration2 = () -> {
            migration2Done.set(true);
            return Migration.Result.COMPLETED;
        };

        Migration.combine(migration1, migration2).run();

        assertThat(migration2Done).isFalse();
    }

    @Test
    public void combineShouldTriggerSecondMigrationWhenTheFirstOneSucceed() {
        AtomicBoolean migration2Done = new AtomicBoolean(false);

        Migration migration1 = () -> Migration.Result.COMPLETED;
        Migration migration2 = () -> {
            migration2Done.set(true);
            return Migration.Result.COMPLETED;
        };

        Migration.combine(migration1, migration2).run();

        assertThat(migration2Done).isTrue();
    }

    @Test
    public void combineShouldExecuteTheFirstMigrationWhenSecondWillFail() {
        AtomicBoolean migration1Done = new AtomicBoolean(false);

        Migration migration1 = () -> {
            migration1Done.set(true);
            return Migration.Result.COMPLETED;
        };
        Migration migration2 = () -> Migration.Result.PARTIAL;


        Migration.combine(migration1, migration2).run();

        assertThat(migration1Done).isTrue();
    }

    @Test
    public void combineShouldExecuteTheFirstMigration() {
        AtomicBoolean migration1Done = new AtomicBoolean(false);

        Migration migration1 = () -> {
            migration1Done.set(true);
            return Migration.Result.COMPLETED;
        };
        Migration migration2 = () -> Migration.Result.COMPLETED;

        Migration.combine(migration1, migration2).run();

        assertThat(migration1Done).isTrue();
    }
}