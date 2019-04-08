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

package org.apache.james.linshare.client;

import static org.apache.james.linshare.LinshareFixture.USER_1;
import static org.apache.james.linshare.LinshareFixture.USER_2;
import static org.apache.james.linshare.LinshareFixture.USER_3;
import static org.apache.james.linshare.LinshareFixture.USER_4;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;

import org.apache.james.core.MailAddress;
import org.apache.james.linshare.LinshareExtension;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import com.github.steveash.guavate.Guavate;

import feign.FeignException;

class LinshareAPITest {

    @RegisterExtension
    static LinshareExtension linshareExtension = new LinshareExtension();

    private LinshareAPI user1LinshareAPI;
    private LinshareAPI user2LinshareAPI;
    private LinshareAPI user3LinshareAPI;
    private LinshareAPI user4LinshareAPI;

    @BeforeEach
    void setup() throws Exception {
        user1LinshareAPI = linshareExtension.getAPIFor(USER_1);
        user2LinshareAPI = linshareExtension.getAPIFor(USER_2);
        user3LinshareAPI = linshareExtension.getAPIFor(USER_3);
        user4LinshareAPI = linshareExtension.getAPIFor(USER_4);
    }

    @Test
    void uploadDocumentShouldReturnUploaded() throws Exception {
        File uploadFile = templateFile();
        Document uploadedDocument = user1LinshareAPI.uploadDocument(uploadFile);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(uploadedDocument.getName()).isEqualTo(uploadFile.getName());
            softly.assertThat(uploadedDocument.getSize()).isEqualTo(uploadFile.length());
        });
    }

    @Test
    void uploadDocumentShouldMakeListingReturnUploaded() throws Exception {
        Document uploadedDocument = user1LinshareAPI.uploadDocument(templateFile());

        assertThat(user1LinshareAPI.listAllDocuments())
            .hasSize(1)
            .containsExactly(uploadedDocument);
    }

    @Test
    void listAllShouldReturnEmptyWhenNoUpload() {
        assertThat(user1LinshareAPI.listAllDocuments())
            .isEmpty();
    }

    @Test
    void listAllShouldReturnAllUploadedDocuments() throws Exception {
        Document firstDocument = user1LinshareAPI.uploadDocument(templateFile());
        Document secondDocument = user1LinshareAPI.uploadDocument(templateFile());

        assertThat(user1LinshareAPI.listAllDocuments())
            .containsExactly(firstDocument, secondDocument);
    }

    @Test
    void listAllShouldNotReturnDocumentsOfOtherUsers() throws Exception {
        Document firstDocument = user1LinshareAPI.uploadDocument(templateFile());
        Document secondDocument = user1LinshareAPI.uploadDocument(templateFile());

        Document user2Document = user2LinshareAPI.uploadDocument(templateFile());

        assertThat(user1LinshareAPI.listAllDocuments())
            .containsExactly(firstDocument, secondDocument);
    }

    @Test
    void deleteShouldDeleteUploadedDocument() throws Exception {
        Document firstDocument = user1LinshareAPI.uploadDocument(templateFile());
        user1LinshareAPI.delete(firstDocument.getId());

        assertThat(user1LinshareAPI.listAllDocuments())
            .isEmpty();
    }

    @Test
    void deleteShouldNotDeleteOtherUserDocuments() throws Exception {
        Document user1Document = user1LinshareAPI.uploadDocument(templateFile());
        Document user2Document = user2LinshareAPI.uploadDocument(templateFile());
        user1LinshareAPI.delete(user1Document.getId());

        assertThat(user2LinshareAPI.listAllDocuments())
            .containsExactly(user2Document);
    }

    @Test
    void deleteShouldReturnErrorWhenDeleteOtherUserDocuments() throws Exception {
        Document user1Document = user1LinshareAPI.uploadDocument(templateFile());

        assertThatThrownBy(() -> user2LinshareAPI.delete(user1Document.getId()))
            .isInstanceOf(FeignException.Forbidden.class);
    }

    @Test
    void deleteAllShouldClearAllDocumentsOfAnUser() throws Exception {
        Document user1Document1 = user1LinshareAPI.uploadDocument(templateFile());
        Document user1Document2 = user1LinshareAPI.uploadDocument(templateFile());

        user1LinshareAPI.deleteAllDocuments();

        assertThat(user1LinshareAPI.listAllDocuments())
            .isEmpty();
    }

    @Test
    void shareShouldShareToTargetedRecipient() throws Exception {
        Document user1Document = user1LinshareAPI.uploadDocument(templateFile());

        ShareRequest shareRequest = ShareRequest.builder()
            .addDocumentId(user1Document.getId())
            .addRecipient(new MailAddress(USER_2.getUsername()))
            .build();

        user1LinshareAPI.share(shareRequest);
        assertThat(user2LinshareAPI.receivedShares())
            .hasSize(1)
            .allSatisfy(shareReceived -> {
                Document sharedDoc = shareReceived.getDocument();
                assertThat(sharedDoc.getName()).isEqualTo(user1Document.getName());
                assertThat(sharedDoc.getSize()).isEqualTo(user1Document.getSize());
            });
    }

    @Test
    void shareShouldWorkWithMultipleRecipients() throws Exception {
        Document user1Document = user1LinshareAPI.uploadDocument(templateFile());

        ShareRequest shareRequest = ShareRequest.builder()
            .addDocumentId(user1Document.getId())
            .addRecipient(new MailAddress(USER_2.getUsername()))
            .addRecipient(new MailAddress(USER_3.getUsername()))
            .addRecipient(new MailAddress(USER_4.getUsername()))
            .build();

        user1LinshareAPI.share(shareRequest);
        List<ReceivedShare> user2Shares = user2LinshareAPI.receivedShares();
        List<ReceivedShare> user3Shares = user3LinshareAPI.receivedShares();
        List<ReceivedShare> user4Shares = user4LinshareAPI.receivedShares();

        assertThat(user2Shares)
            .hasSameSizeAs(user3Shares)
            .hasSameSizeAs(user4Shares)
            .hasSize(1);

        assertThat(sharedDocs(user2Shares, user3Shares, user4Shares))
            .allSatisfy(sharedDoc -> {
                assertThat(sharedDoc.getName()).isEqualTo(user1Document.getName());
                assertThat(sharedDoc.getSize()).isEqualTo(user1Document.getSize());
            });
    }

    private File templateFile() throws Exception {
        return Files.createTempFile("linshare-api-test", ".temp").toFile();
    }

    private List<Document> sharedDocs(List<ReceivedShare>...shares) {
        return ImmutableList.copyOf(shares).stream()
            .flatMap(Collection::stream)
            .map(ReceivedShare::getDocument)
            .collect(Guavate.toImmutableList());
    }
}
