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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.BlobTables;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobStoreFactory;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.mailrepository.MailRepositoryContract;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMailRepositoryTest {
    static final MailRepositoryUrl URL = MailRepositoryUrl.from("proto://url");
    static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraMailRepositoryModule.MODULE,
            CassandraBlobModule.MODULE));

    CassandraMailRepository cassandraMailRepository;

    @Nested
    class PassThroughTest implements MailRepositoryContract {
        @BeforeEach
        void setup(CassandraCluster cassandra) {
            CassandraMailRepositoryMailDaoV2 v2 = new CassandraMailRepositoryMailDaoV2(cassandra.getConf(), BLOB_ID_FACTORY);
            CassandraMailRepositoryKeysDAO keysDAO = new CassandraMailRepositoryKeysDAO(cassandra.getConf(), CassandraConfiguration.DEFAULT_CONFIGURATION);
            BlobStore blobStore = CassandraBlobStoreFactory.forTesting(cassandra.getConf(), new RecordingMetricFactory())
                .passthrough();

            cassandraMailRepository = new CassandraMailRepository(URL,
                keysDAO, v2, MimeMessageStore.factory(blobStore));
        }

        @Override
        public MailRepository retrieveRepository(MailRepositoryPath url) {
            CassandraCluster cassandra = CassandraMailRepositoryTest.cassandraCluster.getCassandraCluster();

            CassandraMailRepositoryMailDaoV2 v2 = new CassandraMailRepositoryMailDaoV2(cassandra.getConf(), BLOB_ID_FACTORY);
            CassandraMailRepositoryKeysDAO keysDAO = new CassandraMailRepositoryKeysDAO(cassandra.getConf(), CassandraConfiguration.DEFAULT_CONFIGURATION);
            BlobStore blobStore = CassandraBlobStoreFactory.forTesting(cassandra.getConf(), new RecordingMetricFactory())
                .passthrough();

           return new CassandraMailRepository(MailRepositoryUrl.fromPathAndProtocol(new Protocol("cassandra"), url), keysDAO, v2, MimeMessageStore.factory(blobStore));
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

        @Test
        @Disabled("depend on setting turn on/off lightweight transaction")
        @Override
        public void storeShouldHaveNoEffectOnSizeWhenAlreadyStoredMail() {
        }

        @Test
        @Disabled("depend on setting turn on/off lightweight transaction")
        @Override
        public void removeShouldHaveNoEffectOnSizeWhenUnknownKeys() {
        }

        @Test
        void removeShouldDeleteStoredBlobs(CassandraCluster cassandra) throws Exception {
            MailRepository testee = retrieveRepository();

            MailKey key1 = testee.store(createMail(MAIL_1));

            testee.remove(key1);

            assertThat(cassandra.getConf().execute(selectFrom(BlobTables.DefaultBucketBlobTable.TABLE_NAME).all().build()))
                .isEmpty();
        }
    }

    @Nested
    class DeDuplicationTest implements MailRepositoryContract {
        @BeforeEach
        void setup(CassandraCluster cassandra) {
            CassandraMailRepositoryMailDaoV2 v2 = new CassandraMailRepositoryMailDaoV2(cassandra.getConf(), BLOB_ID_FACTORY);
            CassandraMailRepositoryKeysDAO keysDAO = new CassandraMailRepositoryKeysDAO(cassandra.getConf(), CassandraConfiguration.DEFAULT_CONFIGURATION);
            BlobStore blobStore = CassandraBlobStoreFactory.forTesting(cassandra.getConf(), new RecordingMetricFactory())
                .deduplication();

            cassandraMailRepository = new CassandraMailRepository(URL,
                keysDAO, v2, MimeMessageStore.factory(blobStore));
        }

        @Override
        public MailRepository retrieveRepository(MailRepositoryPath url) {
            CassandraCluster cassandra = CassandraMailRepositoryTest.cassandraCluster.getCassandraCluster();

            CassandraMailRepositoryMailDaoV2 v2 = new CassandraMailRepositoryMailDaoV2(cassandra.getConf(), BLOB_ID_FACTORY);
            CassandraMailRepositoryKeysDAO keysDAO = new CassandraMailRepositoryKeysDAO(cassandra.getConf(), CassandraConfiguration.DEFAULT_CONFIGURATION);
            BlobStore blobStore = CassandraBlobStoreFactory.forTesting(cassandra.getConf(), new RecordingMetricFactory())
                .deduplication();

            return new CassandraMailRepository(MailRepositoryUrl.fromPathAndProtocol(new Protocol("cassandra"), url), keysDAO, v2, MimeMessageStore.factory(blobStore));
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

        @Test
        @Disabled("depend on setting turn on/off lightweight transaction")
        @Override
        public void storeShouldHaveNoEffectOnSizeWhenAlreadyStoredMail() {
        }

        @Test
        @Disabled("depend on setting turn on/off lightweight transaction")
        @Override
        public void removeShouldHaveNoEffectOnSizeWhenUnknownKeys() {
        }

        @Test
        void removeShouldNotAffectMailsWithTheSameContent() throws Exception {
            MailRepository testee = retrieveRepository();

            MailKey key1 = testee.store(createMail(MAIL_1));
            MailKey key2 = testee.store(createMail(MAIL_2));

            testee.remove(key1);

            assertThatCode(() -> testee.retrieve(key2))
                .doesNotThrowAnyException();
        }
    }

}