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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.mail.internet.MimeMessage;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.cassandra.BlobTable;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobsDAO;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Mail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.google.common.collect.ImmutableList;
import reactor.core.publisher.Mono;

@ExtendWith(CassandraMailRepositoryWithFakeImplementationsTest.MailRepositoryCassandraClusterExtension.class)
class CassandraMailRepositoryWithFakeImplementationsTest {
    private static final MailRepositoryUrl URL = MailRepositoryUrl.from("proto://url");
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    static class MailRepositoryCassandraClusterExtension extends CassandraClusterExtension {
        public MailRepositoryCassandraClusterExtension() {
            super(CassandraModule.aggregateModules(
                    CassandraMailRepositoryModule.MODULE,
                    CassandraBlobModule.MODULE,
                    CassandraSchemaVersionModule.MODULE));
        }

        @Override
        public void afterAll(ExtensionContext extensionContext) {
        }
    }

    @Nested
    class FailingStoreTest {
        CassandraMailRepository cassandraMailRepository;
        CassandraMailRepositoryKeysDAO keysDAO;

        @BeforeEach
        void setup(CassandraCluster cassandra) {
            CassandraMailRepositoryMailDaoAPI mailDAO = new CassandraMailRepositoryMailDAO(cassandra.getConf(), BLOB_ID_FACTORY, cassandra.getTypesProvider());
            keysDAO = new CassandraMailRepositoryKeysDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
            CassandraMailRepositoryCountDAO countDAO = new CassandraMailRepositoryCountDAO(cassandra.getConf());

            cassandraMailRepository = new CassandraMailRepository(URL,
                    keysDAO, countDAO, mailDAO, new FailingStore());
        }

        class FailingStore implements Store<MimeMessage, MimeMessagePartsId> {

            @Override
            public CompletableFuture<MimeMessagePartsId> save(MimeMessage mimeMessage) {
                return CompletableFuture.supplyAsync(() -> {
                    throw new RuntimeException("Expected failure while saving");
                });
            }

            @Override
            public CompletableFuture<MimeMessage> read(MimeMessagePartsId blobIds) {
                return CompletableFuture.supplyAsync(() -> {
                    throw new RuntimeException("Expected failure while reading");
                });
            }
        }

        @Test
        void keysShouldNotBeStoredWhenStoringMimeMessageHasFailed() throws Exception {
            MailKey mailKey = new MailKey("mymail");
            List<MailAddress> recipients = ImmutableList
                    .of(new MailAddress("rec1@domain.com"),
                            new MailAddress("rec2@domain.com"));
            MimeMessage mailContent = MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("test")
                    .setText("this is the content")
                    .build();
            MailImpl mail = new MailImpl(mailKey.asString(), new MailAddress("sender@domain.com"), recipients, mailContent);

            assertThatThrownBy(() -> cassandraMailRepository.store(mail))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("java.lang.RuntimeException: Expected failure while saving");

            assertThat(keysDAO.list(URL).collectList().block()).isEmpty();
        }
    }

    @Nested
    class FailingMailDaoTest {
        CassandraMailRepository cassandraMailRepository;
        CassandraMailRepositoryKeysDAO keysDAO;

        @BeforeEach
        void setup(CassandraCluster cassandra) {
            FailingMailDAO mailDAO = new FailingMailDAO();
            keysDAO = new CassandraMailRepositoryKeysDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
            CassandraMailRepositoryCountDAO countDAO = new CassandraMailRepositoryCountDAO(cassandra.getConf());
            CassandraBlobsDAO blobsDAO = new CassandraBlobsDAO(cassandra.getConf());

            cassandraMailRepository = new CassandraMailRepository(URL,
                    keysDAO, countDAO, mailDAO, MimeMessageStore.factory(blobsDAO).mimeMessageStore());
        }

        class FailingMailDAO implements CassandraMailRepositoryMailDaoAPI {

            FailingMailDAO() {
            }

            @Override
            public CompletableFuture<Void> store(MailRepositoryUrl url, Mail mail, BlobId headerId, BlobId bodyId) {
                return CompletableFuture.supplyAsync(() -> {
                    throw new RuntimeException("Expected failure while storing mail parts");
                });
            }

            @Override
            public Mono<Void> remove(MailRepositoryUrl url, MailKey key) {
                return Mono.fromCallable(() -> {
                    throw new RuntimeException("Expected failure while removing mail parts");
                });

            }

            @Override
            public CompletableFuture<Optional<CassandraMailRepositoryMailDAO.MailDTO>> read(MailRepositoryUrl url, MailKey key) {
                return CompletableFuture.supplyAsync(() -> {
                    throw new RuntimeException("Expected failure while reading mail parts");
                });
            }
        }

        @Test
        void keysShouldNotBeStoredWhenStoringMailPartsHasFailed() throws Exception {
            MailKey mailKey = new MailKey("mymail");
            List<MailAddress> recipients = ImmutableList
                    .of(new MailAddress("rec1@domain.com"),
                            new MailAddress("rec2@domain.com"));
            MimeMessage mailContent = MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("test")
                    .setText("this is the content")
                    .build();
            MailImpl mail = new MailImpl(mailKey.asString(), new MailAddress("sender@domain.com"), recipients, mailContent);

            assertThatThrownBy(() -> cassandraMailRepository.store(mail))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("java.lang.RuntimeException: Expected failure while storing mail parts");

            assertThat(keysDAO.list(URL).collectList().block()).isEmpty();
        }

        @Test
        void mimeMessageShouldBeStoredWhenStoringMailPartsHasFailed(CassandraCluster cassandra) throws Exception {
            MailKey mailKey = new MailKey("mymail");
            List<MailAddress> recipients = ImmutableList
                    .of(new MailAddress("rec1@domain.com"),
                            new MailAddress("rec2@domain.com"));
            MimeMessage mailContent = MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("test")
                    .setText("this is the content")
                    .build();
            MailImpl mail = new MailImpl(mailKey.asString(), new MailAddress("sender@domain.com"), recipients, mailContent);

            assertThatThrownBy(() -> cassandraMailRepository.store(mail))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("java.lang.RuntimeException: Expected failure while storing mail parts");

            ResultSet resultSet = cassandra.getConf().execute(select()
                    .from(BlobTable.TABLE_NAME));
            assertThat(resultSet.all()).hasSize(2);
        }
    }

    @Nested
    class FailingKeysDaoTest {
        CassandraMailRepository cassandraMailRepository;
        CassandraMailRepositoryCountDAO countDAO;

        @BeforeEach
        void setup(CassandraCluster cassandra) {
            CassandraMailRepositoryMailDaoAPI mailDAO = new CassandraMailRepositoryMailDAO(cassandra.getConf(), BLOB_ID_FACTORY, cassandra.getTypesProvider());
            FailingKeysDAO keysDAO = new FailingKeysDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
            countDAO = new CassandraMailRepositoryCountDAO(cassandra.getConf());
            CassandraBlobsDAO blobsDAO = new CassandraBlobsDAO(cassandra.getConf());

            cassandraMailRepository = new CassandraMailRepository(URL,
                    keysDAO, countDAO, mailDAO, MimeMessageStore.factory(blobsDAO).mimeMessageStore());
        }

        class FailingKeysDAO extends CassandraMailRepositoryKeysDAO {

            FailingKeysDAO(Session session, CassandraUtils cassandraUtils) {
                super(session, cassandraUtils);
            }

            @Override
            public Mono<Boolean> store(MailRepositoryUrl url, MailKey key) {
                return Mono.fromCallable(() -> {
                    throw new RuntimeException("Expected failure while storing keys");
                });
            }
        }

        @Test
        void sizeShouldNotBeIncreasedWhenStoringKeysHasFailed() throws Exception {
            MailKey mailKey = new MailKey("mymail");
            List<MailAddress> recipients = ImmutableList
                    .of(new MailAddress("rec1@domain.com"),
                            new MailAddress("rec2@domain.com"));
            MimeMessage mailContent = MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("test")
                    .setText("this is the content")
                    .build();
            MailImpl mail = new MailImpl(mailKey.asString(), new MailAddress("sender@domain.com"), recipients, mailContent);

            assertThatThrownBy(() -> cassandraMailRepository.store(mail))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Expected failure while storing keys");

            assertThat(countDAO.getCount(URL).join()).isEqualTo(0);
        }

        @Test
        void mimeMessageShouldBeStoredWhenStoringKeysHasFailed(CassandraCluster cassandra) throws Exception {
            MailKey mailKey = new MailKey("mymail");
            List<MailAddress> recipients = ImmutableList
                    .of(new MailAddress("rec1@domain.com"),
                            new MailAddress("rec2@domain.com"));
            MimeMessage mailContent = MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("test")
                    .setText("this is the content")
                    .build();
            MailImpl mail = new MailImpl(mailKey.asString(), new MailAddress("sender@domain.com"), recipients, mailContent);

            assertThatThrownBy(() -> cassandraMailRepository.store(mail))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Expected failure while storing keys");

            ResultSet resultSet = cassandra.getConf().execute(select()
                    .from(BlobTable.TABLE_NAME));
            assertThat(resultSet.all()).hasSize(2);
        }

        @Test
        void mailPartsShouldBeStoredWhenStoringKeysHasFailed(CassandraCluster cassandra) throws Exception {
            MailKey mailKey = new MailKey("mymail");
            List<MailAddress> recipients = ImmutableList
                    .of(new MailAddress("rec1@domain.com"),
                            new MailAddress("rec2@domain.com"));
            MimeMessage mailContent = MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("test")
                    .setText("this is the content")
                    .build();
            MailImpl mail = new MailImpl(mailKey.asString(), new MailAddress("sender@domain.com"), recipients, mailContent);

            assertThatThrownBy(() -> cassandraMailRepository.store(mail))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Expected failure while storing keys");

            ResultSet resultSet = cassandra.getConf().execute(select()
                    .from(MailRepositoryTable.CONTENT_TABLE_NAME));
            assertThat(resultSet.all()).hasSize(1);
        }
    }
}