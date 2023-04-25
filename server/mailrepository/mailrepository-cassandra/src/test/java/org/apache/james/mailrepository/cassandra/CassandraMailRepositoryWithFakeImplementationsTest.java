/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.mailrepository.cassandra;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.core.MailImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.cql.ResultSet;

class CassandraMailRepositoryWithFakeImplementationsTest {
    @RegisterExtension
    static CassandraClusterExtension extension = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraMailRepositoryModule.MODULE,
            CassandraBlobModule.MODULE,
            CassandraSchemaVersionModule.MODULE));

    private static final MailRepositoryUrl URL = MailRepositoryUrl.from("proto://url");
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();


    CassandraMailRepository cassandraMailRepository;
    CassandraMailRepositoryKeysDAO keysDAO;

    @BeforeEach
    void setup(CassandraCluster cassandra) {
        CassandraMailRepositoryMailDaoV2 mailDAO = new CassandraMailRepositoryMailDaoV2(cassandra.getConf(), BLOB_ID_FACTORY);
        keysDAO = new CassandraMailRepositoryKeysDAO(cassandra.getConf(), CassandraConfiguration.DEFAULT_CONFIGURATION);
        BlobStore blobStore = CassandraBlobStoreFactory.forTesting(cassandra.getConf(), new RecordingMetricFactory())
            .passthrough();

        cassandraMailRepository = new CassandraMailRepository(URL,
            keysDAO, mailDAO, MimeMessageStore.factory(blobStore));
    }

    @Nested
    class FailingStoreTest {
        @Test
        void keysShouldNotBeStoredWhenStoringMimeMessageHasFailed(CassandraCluster cassandra) throws Exception {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("INSERT INTO blobparts"));

            MailImpl mail = MailImpl.builder()
                .name("mymail")
                .sender("sender@localhost")
                .addRecipient("rec1@domain.com")
                .addRecipient("rec2@domain.com")
                .mimeMessage(MimeMessageBuilder
                    .mimeMessageBuilder()
                    .setSubject("test")
                    .setText("this is the content")
                    .build())
                .build();

            assertThatThrownBy(() -> cassandraMailRepository.store(mail))
                    .isInstanceOf(RuntimeException.class);

            assertThat(keysDAO.list(URL).collectList().block()).isEmpty();
        }
    }

    @Nested
    class FailingMailDaoTest {
        @Test
        void keysShouldNotBeStoredWhenStoringMailPartsHasFailed(CassandraCluster cassandra) throws Exception {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("INSERT INTO mailrepositorycontent"));

            MailImpl mail = MailImpl.builder()
                .name("mymail")
                .sender("sender@localhost")
                .addRecipient("rec1@domain.com")
                .addRecipient("rec2@domain.com")
                .mimeMessage(MimeMessageBuilder
                    .mimeMessageBuilder()
                    .setSubject("test")
                    .setText("this is the content")
                    .build())
                .build();

            assertThatThrownBy(() -> cassandraMailRepository.store(mail))
                    .isInstanceOf(RuntimeException.class);

            assertThat(keysDAO.list(URL).collectList().block()).isEmpty();
        }

        @Test
        void mimeMessageShouldBeStoredWhenStoringMailPartsHasFailed(CassandraCluster cassandra) throws Exception {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("INSERT INTO mailrepositorycontent"));

            MailImpl mail = MailImpl.builder()
                .name("mymail")
                .sender("sender@localhost")
                .addRecipient("rec1@domain.com")
                .addRecipient("rec2@domain.com")
                .mimeMessage(MimeMessageBuilder
                    .mimeMessageBuilder()
                    .setSubject("test")
                    .setText("this is the content")
                    .build())
                .build();

            assertThatThrownBy(() -> cassandraMailRepository.store(mail))
                    .isInstanceOf(RuntimeException.class);

            ResultSet resultSet = cassandra.getConf().execute(selectFrom(BlobTables.DefaultBucketBlobTable.TABLE_NAME).all().build());
            assertThat(resultSet.all()).hasSize(2);
        }
    }

    @Nested
    class FailingKeysDaoTest {
        @Test
        void sizeShouldNotBeIncreasedWhenStoringKeysHasFailed(CassandraCluster cassandra) throws Exception {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("INSERT INTO mailrepositorykeys"));

            MailImpl mail = MailImpl.builder()
                .name("mymail")
                .sender("sender@localhost")
                .addRecipient("rec1@domain.com")
                .addRecipient("rec2@domain.com")
                .mimeMessage(MimeMessageBuilder
                    .mimeMessageBuilder()
                    .setSubject("test")
                    .setText("this is the content")
                    .build())
                .build();

            assertThatThrownBy(() -> cassandraMailRepository.store(mail))
                    .isInstanceOf(RuntimeException.class);

            assertThat(keysDAO.getCount(URL).block()).isEqualTo(0L);
        }

        @Test
        void mimeMessageShouldBeStoredWhenStoringKeysHasFailed(CassandraCluster cassandra) throws Exception {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("INSERT INTO mailrepositorykeys"));

            MailImpl mail = MailImpl.builder()
                .name("mymail")
                .sender("sender@localhost")
                .addRecipient("rec1@domain.com")
                .addRecipient("rec2@domain.com")
                .mimeMessage(MimeMessageBuilder
                    .mimeMessageBuilder()
                    .setSubject("test")
                    .setText("this is the content")
                    .build())
                .build();

            assertThatThrownBy(() -> cassandraMailRepository.store(mail))
                    .isInstanceOf(RuntimeException.class);

            ResultSet resultSet = cassandra.getConf().execute(selectFrom(BlobTables.DefaultBucketBlobTable.TABLE_NAME).all().build());
            assertThat(resultSet.all()).hasSize(2);
        }

        @Test
        void mailPartsShouldBeStoredWhenStoringKeysHasFailed(CassandraCluster cassandra) throws Exception {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("INSERT INTO mailrepositorykeys"));

            MailImpl mail = MailImpl.builder()
                .name("mymail")
                .sender("sender@localhost")
                .addRecipient("rec1@domain.com")
                .addRecipient("rec2@domain.com")
                .mimeMessage(MimeMessageBuilder
                    .mimeMessageBuilder()
                    .setSubject("test")
                    .setText("this is the content")
                    .build())
                .build();

            assertThatThrownBy(() -> cassandraMailRepository.store(mail))
                    .isInstanceOf(RuntimeException.class);

            ResultSet resultSet = cassandra.getConf().execute(selectFrom(MailRepositoryTableV2.CONTENT_TABLE_NAME).all().build());
            assertThat(resultSet.all()).hasSize(1);
        }
    }
}