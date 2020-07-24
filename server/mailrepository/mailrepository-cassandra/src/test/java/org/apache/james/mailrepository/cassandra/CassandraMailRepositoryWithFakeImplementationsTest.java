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

import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.BlobTables;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobStoreFactory;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.server.core.MailImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.driver.core.ResultSet;

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
    CassandraMailRepositoryCountDAO countDAO;
    CassandraMailRepositoryKeysDAO keysDAO;

    @BeforeEach
    void setup(CassandraCluster cassandra) {
        CassandraMailRepositoryMailDaoAPI mailDAO = new CassandraMailRepositoryMailDAO(cassandra.getConf(), BLOB_ID_FACTORY, cassandra.getTypesProvider());
        keysDAO = new CassandraMailRepositoryKeysDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
        countDAO = new CassandraMailRepositoryCountDAO(cassandra.getConf());
        BlobStore blobStore = CassandraBlobStoreFactory.forTesting(cassandra.getConf())
            .passthrough();

        cassandraMailRepository = new CassandraMailRepository(URL,
            keysDAO, countDAO, mailDAO, MimeMessageStore.factory(blobStore).mimeMessageStore());
    }

    @Nested
    class FailingStoreTest {
        @Test
        void keysShouldNotBeStoredWhenStoringMimeMessageHasFailed(CassandraCluster cassandra) throws Exception {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("INSERT INTO blobParts (id,chunkNumber,data) VALUES (:id,:chunkNumber,:data);"));

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
                    .whenQueryStartsWith("INSERT INTO mailRepositoryContent"));

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
                    .whenQueryStartsWith("INSERT INTO mailRepositoryContent"));

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

            ResultSet resultSet = cassandra.getConf().execute(select()
                    .from(BlobTables.DefaultBucketBlobTable.TABLE_NAME));
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
                    .whenQueryStartsWith("INSERT INTO mailRepositoryKeys (name,mailKey)"));

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

            assertThat(countDAO.getCount(URL).block()).isEqualTo(0);
        }

        @Test
        void mimeMessageShouldBeStoredWhenStoringKeysHasFailed(CassandraCluster cassandra) throws Exception {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("INSERT INTO mailRepositoryKeys (name,mailKey)"));

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

            ResultSet resultSet = cassandra.getConf().execute(select()
                    .from(BlobTables.DefaultBucketBlobTable.TABLE_NAME));
            assertThat(resultSet.all()).hasSize(2);
        }

        @Test
        void mailPartsShouldBeStoredWhenStoringKeysHasFailed(CassandraCluster cassandra) throws Exception {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("INSERT INTO mailRepositoryKeys (name,mailKey)"));

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

            ResultSet resultSet = cassandra.getConf().execute(select()
                    .from(MailRepositoryTable.CONTENT_TABLE_NAME));
            assertThat(resultSet.all()).hasSize(1);
        }
    }
}