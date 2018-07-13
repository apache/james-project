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

package org.apache.james.mpt.testsuite;

import java.util.Locale;

import org.apache.james.mpt.HostSystemProvider;
import org.apache.james.mpt.host.ManageSieveHostSystem;
import org.apache.james.mpt.script.SimpleScriptedTestProtocol;
import org.junit.jupiter.api.Test;

public interface HaveSpaceContract extends HostSystemProvider {
    String USER = "user";
    String PASSWORD = "password";

    default SimpleScriptedTestProtocol haveSpaceContractProtocol() throws Exception {
        return new SimpleScriptedTestProtocol("/org/apache/james/managesieve/scripts/", hostSystem())
                .withUser(USER, PASSWORD)
                .withLocale(Locale.US)
                .withPreparedCommand(system ->
                    ((ManageSieveHostSystem) system).setMaxQuota(USER, 50));
    }

    @Test
    default void haveSpaceShouldWork() throws Exception {
        haveSpaceContractProtocol()
            .withLocale(Locale.US)
            .run("havespace");
    }

}
