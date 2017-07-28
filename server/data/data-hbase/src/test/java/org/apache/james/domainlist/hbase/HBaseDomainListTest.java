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
package org.apache.james.domainlist.hbase;

import java.io.IOException;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.AbstractDomainListTest;
import org.apache.james.domainlist.lib.EnvDetector;
import org.apache.james.mailbox.hbase.HBaseClusterSingleton;
import org.apache.james.system.hbase.TablePool;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * Tests for the HBase DomainList implementation.
 *
 * Simply create the needed HBaseDomainList instance, and let the
 * AbstractDomainListTest run the tests
 */
public class HBaseDomainListTest extends AbstractDomainListTest {

    private static final HBaseClusterSingleton cluster = HBaseClusterSingleton.build();

    @BeforeClass
    public static void setMeUp() throws IOException {
        TablePool.getInstance(cluster.getConf());
    }

    /**
     * @see org.apache.james.domainlist.lib.AbstractDomainListTest#createDomainList()
     */
    @Override
    protected DomainList createDomainList() {
        HBaseDomainList domainList = new HBaseDomainList(getDNSServer("localhost"), new EnvDetector());
        domainList.setLog(LoggerFactory.getLogger("MockLog"));
        domainList.setAutoDetect(false);
        domainList.setAutoDetectIP(false);
        return domainList;
    }

    @Ignore
    @Test
    @Override
    public void removeDomainShouldThrowIfTheDomainIsAbsent() throws DomainListException {

    }
}
