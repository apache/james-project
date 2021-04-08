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

import static org.apache.james.linshare.LinshareExtension.LinshareAPIForUserTesting;
import static org.apache.james.linshare.LinshareFixture.USER_1;
import static org.apache.james.linshare.LinshareFixture.USER_2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.apache.james.junit.categories.Unstable;
import org.apache.james.linshare.LinshareExtension;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import feign.FeignException;

@Tag(Unstable.TAG)
class LinshareAPITest {
    private static final String USER1_MAIL = "user1@linshare.org";
    private static final String USER2_MAIL = "user2@linshare.org";


    @RegisterExtension
    static LinshareExtension linshareExtension = new LinshareExtension();

    private LinshareAPIForUserTesting user1LinshareAPI;
    private LinshareAPIForUserTesting user2LinshareAPI;
    private LinshareAPI technicalLinshareAPI;

    @BeforeEach
    void setup() throws Exception {
        user1LinshareAPI = LinshareAPIForUserTesting.from(USER_1);
        user2LinshareAPI = LinshareAPIForUserTesting.from(USER_2);
        technicalLinshareAPI = linshareExtension.getDelegationAccountAPI();
    }

    @Test
    void uploadDocumentShouldReturnUploaded() throws Exception {
        File uploadFile = templateFile();
        Document uploadedDocument = uploadDocumentToUserSpace(USER1_MAIL, uploadFile);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(uploadedDocument.getName()).isEqualTo(uploadFile.getName());
            softly.assertThat(uploadedDocument.getSize()).isEqualTo(uploadFile.length());
        });
    }

    @Test
    void uploadDocumentShouldMakeListingReturnUploaded() throws Exception {
        Document uploadedDocument = uploadDocumentToUserSpace(USER1_MAIL, templateFile());

        assertThat(user1LinshareAPI.listAllDocuments())
            .hasSize(1)
            .containsExactlyInAnyOrder(uploadedDocument);
    }

    @Test
    void listAllShouldReturnEmptyWhenNoUpload() {
        assertThat(user1LinshareAPI.listAllDocuments())
            .isEmpty();
    }

    @Test
    void listAllShouldReturnAllUploadedDocuments() throws Exception {
        Document firstDocument = uploadDocumentToUserSpace(USER1_MAIL, templateFile());
        Document secondDocument = uploadDocumentToUserSpace(USER1_MAIL, templateFile());

        assertThat(user1LinshareAPI.listAllDocuments())
            .containsExactlyInAnyOrder(firstDocument, secondDocument);
    }

    @Test
    void listAllShouldNotReturnDocumentsOfOtherUsers() throws Exception {
        Document firstDocument = uploadDocumentToUserSpace(USER1_MAIL, templateFile());
        Document secondDocument = uploadDocumentToUserSpace(USER1_MAIL, templateFile());

        uploadDocumentToUserSpace(USER2_MAIL, templateFile());

        assertThat(user1LinshareAPI.listAllDocuments())
            .containsExactlyInAnyOrder(firstDocument, secondDocument);
    }

    @Test
    void deleteShouldDeleteUploadedDocument() throws Exception {
        Document firstDocument = uploadDocumentToUserSpace(USER1_MAIL, templateFile());
        user1LinshareAPI.delete(firstDocument.getId());

        assertThat(user1LinshareAPI.listAllDocuments())
            .isEmpty();
    }

    @Test
    void deleteShouldNotDeleteOtherUserDocuments() throws Exception {
        Document user1Document = uploadDocumentToUserSpace(USER1_MAIL, templateFile());
        Document user2Document = uploadDocumentToUserSpace(USER2_MAIL, templateFile());
        user1LinshareAPI.delete(user1Document.getId());

        assertThat(user2LinshareAPI.listAllDocuments())
            .containsExactlyInAnyOrder(user2Document);
    }

    @Test
    void deleteShouldReturnErrorWhenDeleteOtherUserDocuments() throws Exception {
        Document user1Document = uploadDocumentToUserSpace(USER1_MAIL, templateFile());

        assertThatThrownBy(() -> user2LinshareAPI.delete(user1Document.getId()))
            .isInstanceOf(FeignException.Forbidden.class);
    }

    @Test
    void deleteAllShouldClearAllDocumentsOfAnUser() throws Exception {
        uploadDocumentToUserSpace(USER1_MAIL, templateFile());
        uploadDocumentToUserSpace(USER1_MAIL, templateFile());

        user1LinshareAPI.deleteAllDocuments();

        assertThat(user1LinshareAPI.listAllDocuments())
            .isEmpty();
    }

    @Test
    void uploadDocumentShouldUploadToUserSpace() throws Exception {
        Document uploadedDocument = uploadDocumentToUserSpace(USER1_MAIL, templateFile());

        List<Document> user1Documents = user1LinshareAPI.listAllDocuments();
        assertThat(user1Documents).containsAnyOf(uploadedDocument);
    }

    @Test
    void uploadDocumentShouldPerformByDelegationAccount() throws Exception {
        Document uploadedDocument = uploadDocumentToUserSpace(USER1_MAIL, templateFile());

        List<Document> user1Documents = user1LinshareAPI.listAllDocuments();
        assertThat(user1Documents).containsAnyOf(uploadedDocument);
    }

    private Document uploadDocumentToUserSpace(String targetUserEmail, File userFile) {
        User targetUser = technicalLinshareAPI.getUserByMail(targetUserEmail);
        return technicalLinshareAPI.uploadDocumentByDelegation(targetUser, userFile);
    }

    private File templateFile() throws Exception {
        return Files.createTempFile("linshare-api-test", ".temp").toFile();
    }
}
