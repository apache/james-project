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

package org.apache.james.mailbox.cassandra.mail.migration;

public interface Migration {

    enum MigrationResult {
        COMPLETED,
        PARTIAL
    }

    static MigrationResult combine(MigrationResult result1, MigrationResult result2) {
        if (result1 == MigrationResult.COMPLETED
            && result2 == MigrationResult.COMPLETED) {
            return MigrationResult.COMPLETED;
        }
        return MigrationResult.PARTIAL;
    }

    /**
     * Runs the migration
     *
     * @return Return true if fully migrated. Returns false otherwise.
     */
    MigrationResult run();
}
