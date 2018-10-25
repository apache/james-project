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

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

class CassandraMailRepositoryMailDAOTest {
    static final MailRepositoryUrl URL = MailRepositoryUrl.from("proto://url");
    static final MailKey KEY_1 = new MailKey("key1");
    static final TestBlobId.Factory BLOB_ID_FACTORY = new TestBlobId.Factory();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraMailRepositoryModule.MODULE);

    CassandraMailRepositoryMailDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraMailRepositoryMailDAO(cassandra.getConf(), BLOB_ID_FACTORY, cassandra.getTypesProvider());
    }

    @Test
    void readShouldReturnEmptyWhenAbsent() {
        assertThat(testee.read(URL, KEY_1).join())
            .isEmpty();
    }

    @Test
    void readShouldReturnAllMailMetadata() throws Exception {
        BlobId blobIdBody = BLOB_ID_FACTORY.from("blobHeader");
        BlobId blobIdHeader = BLOB_ID_FACTORY.from("blobBody");
        String errorMessage = "error message";
        String state = "state";
        String remoteAddr = "remoteAddr";
        String remoteHost = "remoteHost";
        PerRecipientHeaders.Header header = PerRecipientHeaders.Header.builder().name("headerName").value("headerValue").build();
        String attributeName = "att1";
        ImmutableList<String> attributeValue = ImmutableList.of("value1", "value2");

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
                .attribute(attributeName, attributeValue)
                .build(),
            blobIdHeader,
            blobIdBody)
            .join();

        CassandraMailRepositoryMailDAO.MailDTO mailDTO = testee.read(URL, KEY_1).join().get();

        Mail partialMail = mailDTO.getMailBuilder().build();
        assertSoftly(softly -> {
            softly.assertThat(mailDTO.getBodyBlobId()).isEqualTo(blobIdBody);
            softly.assertThat(mailDTO.getHeaderBlobId()).isEqualTo(blobIdHeader);
            softly.assertThat(partialMail.getName()).isEqualTo(KEY_1.asString());
            softly.assertThat(partialMail.getErrorMessage()).isEqualTo(errorMessage);
            softly.assertThat(partialMail.getState()).isEqualTo(state);
            softly.assertThat(partialMail.getRemoteAddr()).isEqualTo(remoteAddr);
            softly.assertThat(partialMail.getRemoteHost()).isEqualTo(remoteHost);
            softly.assertThat(partialMail.getAttributeNames()).containsOnly(attributeName);
            softly.assertThat(partialMail.getAttribute(attributeName)).isEqualTo(attributeValue);
            softly.assertThat(partialMail.getPerRecipientSpecificHeaders().getRecipientsWithSpecificHeaders())
                    .containsOnly(MailAddressFixture.RECIPIENT1);
            softly.assertThat(partialMail.getPerRecipientSpecificHeaders().getHeadersForRecipient(MailAddressFixture.RECIPIENT1))
                    .containsOnly(header);
            softly.assertThat(partialMail.getMaybeSender().asOptional()).contains(MailAddressFixture.SENDER);
            softly.assertThat(partialMail.getRecipients()).containsOnly(MailAddressFixture.RECIPIENT1, MailAddressFixture.RECIPIENT2);
        });
    }

    @Test
    void storeShouldAcceptMailWithOnlyName() throws Exception {
        BlobId blobIdBody = BLOB_ID_FACTORY.from("blobHeader");
        BlobId blobIdHeader = BLOB_ID_FACTORY.from("blobBody");

        testee.store(URL,
            FakeMail.builder()
                .name(KEY_1.asString())
                .build(),
            blobIdHeader,
            blobIdBody)
            .join();

        CassandraMailRepositoryMailDAO.MailDTO mailDTO = testee.read(URL, KEY_1).join().get();

        Mail partialMail = mailDTO.getMailBuilder().build();
        assertSoftly(softly -> {
            softly.assertThat(mailDTO.getBodyBlobId()).isEqualTo(blobIdBody);
            softly.assertThat(mailDTO.getHeaderBlobId()).isEqualTo(blobIdHeader);
            softly.assertThat(partialMail.getName()).isEqualTo(KEY_1.asString());
        });
    }

    @Test
    void removeShouldDeleteMailMetaData() throws Exception {
        BlobId blobIdBody = BLOB_ID_FACTORY.from("blobHeader");
        BlobId blobIdHeader = BLOB_ID_FACTORY.from("blobBody");

        testee.store(URL,
            FakeMail.builder()
                .name(KEY_1.asString())
                .build(),
            blobIdHeader,
            blobIdBody)
            .join();

        testee.remove(URL, KEY_1).join();

        assertThat(testee.read(URL, KEY_1).join())
            .isEmpty();
    }

}