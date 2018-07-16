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

package org.apache.james.mpt.imapmailbox.cassandra;

import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.cassandra.host.CassandraHostSystemRule;
import org.apache.james.mpt.imapmailbox.suite.Condstore;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.RuleChain;

public class CassandraCondstoreTest extends Condstore {

    private static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    private static CassandraHostSystemRule cassandraHostSystemRule = new CassandraHostSystemRule(cassandraServer);

    /**
     * The underlying tests are playing with capabilities, which are configured when starting James.
     * Hence, we can't use the RuleChain as a @ClassRule in this implementation.
     */
    @Rule
    public RuleChain ruleChaine = RuleChain.outerRule(cassandraServer).around(cassandraHostSystemRule);

    @Override
    protected JamesImapHostSystem createJamesImapHostSystem() {
        return cassandraHostSystemRule.getImapHostSystem();
    }

    @After
    public void tearDown() throws Exception {
        cassandraHostSystemRule.clean();
    }
}
