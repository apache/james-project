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

package org.apache.james.mailbox.cassandra.quota;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.core.quota.QuotaLimit;
import org.apache.james.core.quota.quotacomponent.MailBoxQuotaComponent;
import org.apache.james.core.quota.quotascope.UserQuotaScope;
import org.apache.james.core.quota.quotatype.SizeQuotaType;
import org.apache.james.mailbox.cassandra.mail.utils.GuiceUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraQuotaModule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraQuotaLimitDaoTest {

    private CassandraQuotaLimitDao cassandraQuotaLimitDao;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        CassandraBlobModule.MODULE,
        CassandraQuotaModule.MODULE));

    @BeforeEach
    private void setup() {
        cassandraQuotaLimitDao = GuiceUtils.testInjector(cassandraCluster.getCassandraCluster()).getInstance(CassandraQuotaLimitDao.class);
    }

    @Test
    void setQuotaLimitShouldSaveObjectSuccessfully() {
        QuotaLimit expected = QuotaLimit.of("A", MailBoxQuotaComponent.INSTANCE, SizeQuotaType.INSTANCE, UserQuotaScope.INSTANCE, 100l);
        cassandraQuotaLimitDao.setQuotaLimit(expected).block();

        QuotaLimit actual = cassandraQuotaLimitDao.getQuotaLimit("A", MailBoxQuotaComponent.INSTANCE, SizeQuotaType.INSTANCE, UserQuotaScope.INSTANCE).block();
        Assertions.assertEquals(expected, actual);
    }

}
