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

package org.apache.james.mailrepository.cassandra;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobStore;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.mailrepository.MailRepositoryContract;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMailRepositoryTest implements MailRepositoryContract {
    static final MailRepositoryUrl URL = MailRepositoryUrl.from("proto://url");
    static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraMailRepositoryModule.MODULE,
            CassandraBlobModule.MODULE));

    CassandraMailRepository cassandraMailRepository;


    @BeforeEach
    void setup(CassandraCluster cassandra) {
        CassandraMailRepositoryMailDAO v1 = new CassandraMailRepositoryMailDAO(cassandra.getConf(), BLOB_ID_FACTORY, cassandra.getTypesProvider());
        CassandraMailRepositoryMailDaoV2 v2 = new CassandraMailRepositoryMailDaoV2(cassandra.getConf(), BLOB_ID_FACTORY);
        CassandraMailRepositoryMailDaoAPI mailDAO = new MergingCassandraMailRepositoryMailDao(v1, v2);
        CassandraMailRepositoryKeysDAO keysDAO = new CassandraMailRepositoryKeysDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
        CassandraMailRepositoryCountDAO countDAO = new CassandraMailRepositoryCountDAO(cassandra.getConf());
        CassandraBlobStore blobStore = CassandraBlobStore.forTesting(cassandra.getConf());

        cassandraMailRepository = new CassandraMailRepository(URL,
            keysDAO, countDAO, mailDAO, MimeMessageStore.factory(blobStore).mimeMessageStore());
    }

    @Override
    public MailRepository retrieveRepository() {
        return cassandraMailRepository;
    }

    @Test
    @Disabled("key is unique in Cassandra")
    @Override
    public void sizeShouldBeIncrementedByOneWhenDuplicates() {
    }

}