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

package org.apache.james.mailrepository.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.MessagingException;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.blob.memory.MemoryBlobStoreFactory;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Attribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresMailRepositoryBlobReferenceSourceTest {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresModule.aggregateModules(PostgresMailRepositoryModule.MODULE));

    private static final MailRepositoryUrl URL = MailRepositoryUrl.fromPathAndProtocol(new Protocol("postgres"), MailRepositoryPath.from("testrepo"));

    PostgresMailRepositoryContentDAO postgresMailRepositoryContentDAO;
    PostgresMailRepositoryBlobReferenceSource postgresMailRepositoryBlobReferenceSource;

    @BeforeEach
    void beforeEach() {
        BlobId.Factory factory = new HashBlobId.Factory();
        BlobStore blobStore = MemoryBlobStoreFactory.builder()
            .blobIdFactory(factory)
            .defaultBucketName()
            .passthrough();
        postgresMailRepositoryContentDAO = new PostgresMailRepositoryContentDAO(postgresExtension.getPostgresExecutor(), MimeMessageStore.factory(blobStore), factory);
        postgresMailRepositoryBlobReferenceSource = new PostgresMailRepositoryBlobReferenceSource(postgresMailRepositoryContentDAO);
    }

    @Test
    void blobReferencesShouldBeEmptyByDefault() {
        assertThat(postgresMailRepositoryBlobReferenceSource.listReferencedBlobs().collectList().block())
            .isEmpty();
    }

    @Test
    void blobReferencesShouldReturnAllBlobs() throws Exception {
        postgresMailRepositoryContentDAO.store(createMail(new MailKey("mail1")), URL);
        postgresMailRepositoryContentDAO.store(createMail(new MailKey("mail2")), URL);

        assertThat(postgresMailRepositoryBlobReferenceSource.listReferencedBlobs().collectList().block())
            .hasSize(4);
    }

    private MailImpl createMail(MailKey key) throws MessagingException {
        return MailImpl.builder()
            .name(key.asString())
            .sender("sender@localhost")
            .addRecipient("rec1@domain.com")
            .addRecipient("rec2@domain.com")
            .addAttribute(Attribute.convertToAttribute("testAttribute", "testValue"))
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .setSubject("test")
                .setText("original body")
                .build())
            .build();
    }

}
