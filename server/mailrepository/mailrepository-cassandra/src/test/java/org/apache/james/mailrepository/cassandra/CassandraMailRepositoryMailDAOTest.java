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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.List;
import java.util.Optional;

import javax.mail.MessagingException;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

class CassandraMailRepositoryMailDAOTest {
    static final MailRepositoryUrl URL = MailRepositoryUrl.from("proto://url");
    static final MailKey KEY_1 = new MailKey("key1");
    static final TestBlobId.Factory BLOB_ID_FACTORY = new TestBlobId.Factory();

    public static final CassandraModule MODULE = CassandraModule.aggregateModules(
            CassandraMailRepositoryModule.MODULE,
            CassandraSchemaVersionModule.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);


    abstract class TestSuite {

        abstract CassandraMailRepositoryMailDaoAPI testee();

        @Test
        void storeShouldAcceptMailWithOnlyName() throws Exception {
            CassandraMailRepositoryMailDaoAPI testee = testee();
            BlobId blobIdBody = BLOB_ID_FACTORY.from("blobHeader");
            BlobId blobIdHeader = BLOB_ID_FACTORY.from("blobBody");

            testee.store(URL,
                FakeMail.builder()
                    .name(KEY_1.asString())
                    .build(),
                blobIdHeader,
                blobIdBody)
                .block();

            CassandraMailRepositoryMailDAO.MailDTO mailDTO = testee.read(URL, KEY_1).block().get();

            Mail partialMail = mailDTO.getMailBuilder().build();
            assertSoftly(softly -> {
                softly.assertThat(mailDTO.getBodyBlobId()).isEqualTo(blobIdBody);
                softly.assertThat(mailDTO.getHeaderBlobId()).isEqualTo(blobIdHeader);
                softly.assertThat(partialMail.getName()).isEqualTo(KEY_1.asString());
            });
        }

        @Test
        void removeShouldDeleteMailMetaData() throws Exception {
            CassandraMailRepositoryMailDaoAPI testee = testee();
            BlobId blobIdBody = BLOB_ID_FACTORY.from("blobHeader");
            BlobId blobIdHeader = BLOB_ID_FACTORY.from("blobBody");

            testee.store(URL,
                FakeMail.builder()
                    .name(KEY_1.asString())
                    .build(),
                blobIdHeader,
                blobIdBody)
                .block();

            testee.remove(URL, KEY_1).block();

            assertThat(testee.read(URL, KEY_1).block())
                .isEmpty();
        }


        @Test
        void readShouldReturnEmptyWhenAbsent() {
            assertThat(testee().read(URL, KEY_1).block())
                .isEmpty();
        }
    }

    @Nested
    class V1 extends TestSuite {

        private CassandraMailRepositoryMailDAO testee;

        @BeforeEach
        void setUp(CassandraCluster cassandra) {
            testee = new CassandraMailRepositoryMailDAO(cassandra.getConf(), BLOB_ID_FACTORY, cassandra.getTypesProvider());
        }

        @Override
        CassandraMailRepositoryMailDaoAPI testee() {
            return testee;
        }

        @Test
        void readShouldReturnAllMailMetadata() throws Exception {
            CassandraMailRepositoryMailDaoAPI testee = testee();

            BlobId blobIdBody = BLOB_ID_FACTORY.from("blobHeader");
            BlobId blobIdHeader = BLOB_ID_FACTORY.from("blobBody");
            String errorMessage = "error message";
            String state = "state";
            String remoteAddr = "remoteAddr";
            String remoteHost = "remoteHost";
            PerRecipientHeaders.Header header = PerRecipientHeaders.Header.builder().name("headerName").value("headerValue").build();
            ImmutableList<String> attributeValue = ImmutableList.of("value1", "value2");
            Attribute attribute = Attribute.convertToAttribute("att1", attributeValue);

            testee.store(URL,
                FakeMail.builder()
                    .name(KEY_1.asString())
                    .sender(MailAddressFixture.SENDER)
                    .recipients(MailAddressFixture.RECIPIENT1, MailAddressFixture.RECIPIENT2)
                    .errorMessage(errorMessage)
                    .state(state)
                    .remoteAddr(remoteAddr)
                    .remoteHost(remoteHost)
                    .addHeaderForRecipient(header, MailAddressFixture.RECIPIENT1)
                    .attribute(attribute)
                    .build(),
                blobIdHeader,
                blobIdBody)
                .block();

            CassandraMailRepositoryMailDAO.MailDTO mailDTO = testee.read(URL, KEY_1).block().get();

            Mail partialMail = mailDTO.getMailBuilder().build();
            assertSoftly(softly -> {
                softly.assertThat(mailDTO.getBodyBlobId()).isEqualTo(blobIdBody);
                softly.assertThat(mailDTO.getHeaderBlobId()).isEqualTo(blobIdHeader);
                softly.assertThat(partialMail.getName()).isEqualTo(KEY_1.asString());
                softly.assertThat(partialMail.getErrorMessage()).isEqualTo(errorMessage);
                softly.assertThat(partialMail.getState()).isEqualTo(state);
                softly.assertThat(partialMail.getRemoteAddr()).isEqualTo(remoteAddr);
                softly.assertThat(partialMail.getRemoteHost()).isEqualTo(remoteHost);
                softly.assertThat(partialMail.attributes()).containsOnly(attribute);
                softly.assertThat(partialMail.getAttribute(attribute.getName())).contains(attribute);
                softly.assertThat(partialMail.getPerRecipientSpecificHeaders().getRecipientsWithSpecificHeaders())
                    .containsOnly(MailAddressFixture.RECIPIENT1);
                softly.assertThat(partialMail.getPerRecipientSpecificHeaders().getHeadersForRecipient(MailAddressFixture.RECIPIENT1))
                    .containsOnly(header);
                softly.assertThat(partialMail.getMaybeSender().asOptional()).contains(MailAddressFixture.SENDER);
                softly.assertThat(partialMail.getRecipients()).containsOnly(MailAddressFixture.RECIPIENT1, MailAddressFixture.RECIPIENT2);
            });
        }
    }

    @Nested
    class V2 extends TestSuite {

        private CassandraMailRepositoryMailDaoV2 testee;

        @BeforeEach
        void setUp(CassandraCluster cassandra) {
            testee = new CassandraMailRepositoryMailDaoV2(cassandra.getConf(), BLOB_ID_FACTORY);
        }

        @Override
        CassandraMailRepositoryMailDaoAPI testee() {
            return testee;
        }

        @Test
        void readShouldReturnAllMailMetadata() throws Exception {
            CassandraMailRepositoryMailDaoAPI testee = testee();

            BlobId blobIdBody = BLOB_ID_FACTORY.from("blobHeader");
            BlobId blobIdHeader = BLOB_ID_FACTORY.from("blobBody");
            String errorMessage = "error message";
            String state = "state";
            String remoteAddr = "remoteAddr";
            String remoteHost = "remoteHost";
            PerRecipientHeaders.Header header = PerRecipientHeaders.Header.builder().name("headerName").value("headerValue").build();
            AttributeName attributeName = AttributeName.of("att1");
            List<AttributeValue<?>> attributeValue = ImmutableList.of(AttributeValue.of("value1"), AttributeValue.of("value2"));
            Attribute attribute = new Attribute(attributeName, AttributeValue.of(attributeValue));
            List<Attribute> attributes = ImmutableList.of(attribute);

            testee.store(URL,
                FakeMail.builder()
                    .name(KEY_1.asString())
                    .sender(MailAddressFixture.SENDER)
                    .recipients(MailAddressFixture.RECIPIENT1, MailAddressFixture.RECIPIENT2)
                    .errorMessage(errorMessage)
                    .state(state)
                    .remoteAddr(remoteAddr)
                    .remoteHost(remoteHost)
                    .addHeaderForRecipient(header, MailAddressFixture.RECIPIENT1)
                    .attributes(attributes)
                    .build(),
                blobIdHeader,
                blobIdBody)
                .block();

            CassandraMailRepositoryMailDAO.MailDTO mailDTO = testee.read(URL, KEY_1).block().get();

            Mail partialMail = mailDTO.getMailBuilder().build();
            assertSoftly(softly -> {
                softly.assertThat(mailDTO.getBodyBlobId()).isEqualTo(blobIdBody);
                softly.assertThat(mailDTO.getHeaderBlobId()).isEqualTo(blobIdHeader);
                softly.assertThat(partialMail.getName()).isEqualTo(KEY_1.asString());
                softly.assertThat(partialMail.getErrorMessage()).isEqualTo(errorMessage);
                softly.assertThat(partialMail.getState()).isEqualTo(state);
                softly.assertThat(partialMail.getRemoteAddr()).isEqualTo(remoteAddr);
                softly.assertThat(partialMail.getRemoteHost()).isEqualTo(remoteHost);
                softly.assertThat(partialMail.attributeNames()).containsOnly(attributeName);
                softly.assertThat(partialMail.getAttribute(attributeName)).contains(attribute);
                softly.assertThat(partialMail.getPerRecipientSpecificHeaders().getRecipientsWithSpecificHeaders())
                    .containsOnly(MailAddressFixture.RECIPIENT1);
                softly.assertThat(partialMail.getPerRecipientSpecificHeaders().getHeadersForRecipient(MailAddressFixture.RECIPIENT1))
                    .containsOnly(header);
                softly.assertThat(partialMail.getMaybeSender().asOptional()).contains(MailAddressFixture.SENDER);
                softly.assertThat(partialMail.getRecipients()).containsOnly(MailAddressFixture.RECIPIENT1, MailAddressFixture.RECIPIENT2);
            });
        }
    }

    @Nested
    class Merging extends TestSuite {

        private MergingCassandraMailRepositoryMailDao testee;
        private CassandraMailRepositoryMailDAO v1;
        private CassandraMailRepositoryMailDaoV2 v2;

        @BeforeEach
        void setUp(CassandraCluster cassandra) {
            v1 = new CassandraMailRepositoryMailDAO(cassandra.getConf(), BLOB_ID_FACTORY, cassandra.getTypesProvider());
            v2 = new CassandraMailRepositoryMailDaoV2(cassandra.getConf(), BLOB_ID_FACTORY);
            testee = new MergingCassandraMailRepositoryMailDao(v1, v2);
        }

        @Override
        CassandraMailRepositoryMailDaoAPI testee() {
            return testee;
        }

        @Test
        void readShouldReturnV1Value() throws MessagingException {
            BlobId blobIdBody = BLOB_ID_FACTORY.from("blobHeader");
            BlobId blobIdHeader = BLOB_ID_FACTORY.from("blobBody");

            v1.store(URL,
                FakeMail.builder()
                    .name(KEY_1.asString())
                    .build(),
                blobIdHeader,
                blobIdBody)
                .block();

            CassandraMailRepositoryMailDaoAPI.MailDTO actual = testee.read(URL, KEY_1).block().get();
            Mail partialMail = actual.getMailBuilder().build();
            assertSoftly(softly -> {
                softly.assertThat(actual.getBodyBlobId()).isEqualTo(blobIdBody);
                softly.assertThat(actual.getHeaderBlobId()).isEqualTo(blobIdHeader);
                softly.assertThat(partialMail.getName()).isEqualTo(KEY_1.asString());
            });
        }

        @Test
        void readShouldReturnV2Value() throws MessagingException {
            BlobId blobIdBody = BLOB_ID_FACTORY.from("blobHeader");
            BlobId blobIdHeader = BLOB_ID_FACTORY.from("blobBody");

            v2.store(URL,
                FakeMail.builder()
                    .name(KEY_1.asString())
                    .build(),
                blobIdHeader,
                blobIdBody)
                .block();

            CassandraMailRepositoryMailDaoAPI.MailDTO actual = testee.read(URL, KEY_1).block().get();
            Mail partialMail = actual.getMailBuilder().build();
            assertSoftly(softly -> {
                softly.assertThat(actual.getBodyBlobId()).isEqualTo(blobIdBody);
                softly.assertThat(actual.getHeaderBlobId()).isEqualTo(blobIdHeader);
                softly.assertThat(partialMail.getName()).isEqualTo(KEY_1.asString());
            });
        }

        @Test
        void readShouldReturnV2ValueIfPresentInBoth() throws MessagingException {
            BlobId blobIdBody1 = BLOB_ID_FACTORY.from("blobHeader");
            BlobId blobIdBody2 = BLOB_ID_FACTORY.from("blobHeader2");
            BlobId blobIdHeader1 = BLOB_ID_FACTORY.from("blobBody");
            BlobId blobIdHeader2 = BLOB_ID_FACTORY.from("blobBody2");

            v1.store(URL,
                FakeMail.builder()
                    .name(KEY_1.asString())
                    .build(),
                blobIdHeader1,
                blobIdBody1)
                .block();

            v2.store(URL,
                FakeMail.builder()
                    .name(KEY_1.asString())
                    .build(),
                blobIdHeader2,
                blobIdBody2)
                .block();

            CassandraMailRepositoryMailDaoAPI.MailDTO actual = testee.read(URL, KEY_1).block().get();
            Mail partialMail = actual.getMailBuilder().build();
            assertSoftly(softly -> {
                softly.assertThat(actual.getBodyBlobId()).isEqualTo(blobIdBody2);
                softly.assertThat(actual.getHeaderBlobId()).isEqualTo(blobIdHeader2);
                softly.assertThat(partialMail.getName()).isEqualTo(KEY_1.asString());
            });
        }

        @Test
        void removeShouldRemoveInBOth() throws MessagingException {
            BlobId blobIdBody1 = BLOB_ID_FACTORY.from("blobHeader");
            BlobId blobIdBody2 = BLOB_ID_FACTORY.from("blobHeader2");
            BlobId blobIdHeader1 = BLOB_ID_FACTORY.from("blobBody");
            BlobId blobIdHeader2 = BLOB_ID_FACTORY.from("blobBody2");

            v1.store(URL,
                FakeMail.builder()
                    .name(KEY_1.asString())
                    .build(),
                blobIdHeader1,
                blobIdBody1)
                .block();

            v2.store(URL,
                FakeMail.builder()
                    .name(KEY_1.asString())
                    .build(),
                blobIdHeader2,
                blobIdBody2)
                .block();

            testee.remove(URL, KEY_1).block();

            Optional<CassandraMailRepositoryMailDaoAPI.MailDTO> v1Entry = v1.read(URL, KEY_1).block();
            Optional<CassandraMailRepositoryMailDaoAPI.MailDTO> v2Entry = v2.read(URL, KEY_1).block();
            assertThat(v1Entry).isEmpty();
            assertThat(v2Entry).isEmpty();
        }
    }
}