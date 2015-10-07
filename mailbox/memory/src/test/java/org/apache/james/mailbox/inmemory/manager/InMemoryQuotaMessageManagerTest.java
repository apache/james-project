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
package org.apache.james.mailbox.inmemory.manager;

import org.apache.james.mailbox.manager.ManagerTestResources;
import org.apache.james.mailbox.manager.QuotaMessageManagerTest;

/**
 * Test for quota support upon basic Message manager operation.
 *
 * Tests are performed with sufficient rights to ensure all underlying functions behave well.
 * Quota are adjusted and we check that exceptions are well thrown.
 */
public class InMemoryQuotaMessageManagerTest extends QuotaMessageManagerTest {

    @Override
    protected ManagerTestResources createResources() throws Exception {
        return new ManagerTestResources(new InMemoryIntegrationResources());
    }

}