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

package org.apache.james.mpt.imapmailbox.postgres;

import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.postgres.host.PostgresHostSystemExtension;
import org.apache.james.mpt.imapmailbox.suite.SelectedState;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresSelectedStateTest extends SelectedState {
    @RegisterExtension
    public static PostgresHostSystemExtension hostSystemExtension = new PostgresHostSystemExtension();

    @Override
    protected ImapHostSystem createImapHostSystem() {
        return hostSystemExtension.getHostSystem();
    }

    @Override
    public void testCopyITALY() {
    }

    @Override
    public void testCopyKOREA() {
    }

    @Override
    public void testCopyUS() {
    }

    @Override
    public void testUidITALY() {
    }

    @Override
    public void testUidKOREA() {
    }

    @Override
    public void testUidUS() {
    }

    @Override
    @Disabled("SEARCH save date just return empty result for JPA")
    public void testSearchSaveDate() {
    }
}