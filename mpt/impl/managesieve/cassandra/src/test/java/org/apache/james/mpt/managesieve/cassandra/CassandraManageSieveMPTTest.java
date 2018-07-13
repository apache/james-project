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

package org.apache.james.mpt.managesieve.cassandra;

import org.apache.james.backends.cassandra.DockerCassandraExtension;
import org.apache.james.mpt.host.ManageSieveHostSystem;
import org.apache.james.mpt.managesieve.cassandra.host.CassandraHostSystem;
import org.apache.james.mpt.testsuite.AuthenticateContract;
import org.apache.james.mpt.testsuite.CapabilityContract;
import org.apache.james.mpt.testsuite.CheckScriptContract;
import org.apache.james.mpt.testsuite.DeleteScriptContract;
import org.apache.james.mpt.testsuite.GetScriptContract;
import org.apache.james.mpt.testsuite.HaveSpaceContract;
import org.apache.james.mpt.testsuite.ListScriptsContract;
import org.apache.james.mpt.testsuite.LogoutContract;
import org.apache.james.mpt.testsuite.NoopContract;
import org.apache.james.mpt.testsuite.PutScriptContract;
import org.apache.james.mpt.testsuite.RenameScriptContract;
import org.apache.james.mpt.testsuite.SetActiveContract;
import org.apache.james.mpt.testsuite.StartTlsContract;
import org.apache.james.mpt.testsuite.UnauthenticatedContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerCassandraExtension.class)
class CassandraManageSieveMPTTest implements AuthenticateContract, CapabilityContract, CheckScriptContract,
                                             DeleteScriptContract, GetScriptContract, HaveSpaceContract,
                                             ListScriptsContract, LogoutContract, NoopContract, PutScriptContract,
                                             RenameScriptContract, SetActiveContract, StartTlsContract,
                                             UnauthenticatedContract {
    private ManageSieveHostSystem system;

    @BeforeEach
    void setUp(DockerCassandraExtension.DockerCassandra dockerCassandra) throws Exception {
        system = new CassandraHostSystem(dockerCassandra.getHost());
        system.beforeTest();
    }

    @Override
    public ManageSieveHostSystem hostSystem() {
        return system;
    }

    @AfterEach
    void tearDown() throws Exception {
        hostSystem().afterTest();
    }
}
