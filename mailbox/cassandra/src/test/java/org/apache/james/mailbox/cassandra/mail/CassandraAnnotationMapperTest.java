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

package org.apache.james.mailbox.cassandra.mail;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.blob.cassandra.CassandraBlobDataDefinition;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.utils.GuiceUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraAnnotationDataDefinition;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.model.AnnotationMapperTest;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraAnnotationMapperTest extends AnnotationMapperTest {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraDataDefinition.aggregateModules(
        CassandraBlobDataDefinition.MODULE,
        CassandraAnnotationDataDefinition.MODULE));

    @Override
    protected AnnotationMapper createAnnotationMapper() {
        return GuiceUtils.testInjector(cassandraCluster.getCassandraCluster())
            .getInstance(CassandraAnnotationMapper.class);
    }

    @Override
    protected MailboxId generateMailboxId() {
        return CassandraId.timeBased();
    }
}
