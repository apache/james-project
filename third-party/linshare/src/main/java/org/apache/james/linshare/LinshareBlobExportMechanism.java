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

package org.apache.james.linshare;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.blob.export.api.ExportedFileNamesGenerator;
import org.apache.james.blob.export.api.FileExtension;
import org.apache.james.core.MailAddress;
import org.apache.james.linshare.client.LinshareAPI;
import org.apache.james.linshare.client.User;

import com.google.common.io.Files;

public class LinshareBlobExportMechanism implements BlobExportMechanism {

    private final LinshareAPI linshareAPI;
    private final BlobStore blobStore;
    private final File tempDir;

    @Inject
    LinshareBlobExportMechanism(LinshareAPI linshareAPI, BlobStore blobStore) {
        this.linshareAPI = linshareAPI;
        this.blobStore = blobStore;
        this.tempDir = Files.createTempDir();
    }

    @Override
    public ShareeStage blobId(BlobId blobId) {
        return mailAddress -> explanation -> fileCustomPrefix -> fileExtension -> () ->  {
            try {
                exportBlob(blobId, mailAddress, fileCustomPrefix, fileExtension);
            } catch (Exception e) {
                throw new BlobExportException(String.format("Error while exporting blob %s to %s", blobId.asString(),
                    mailAddress.asString()), e);
            }
        };
    }

    private void exportBlob(BlobId blobId, MailAddress mailAddress, Optional<String> fileCustomPrefix,
                            Optional<FileExtension> fileExtension) throws IOException {
        String fileName = ExportedFileNamesGenerator.generateFileName(fileCustomPrefix, blobId, fileExtension);
        File tempFile = new File(tempDir, fileName);
        try (InputStream in = blobStore.read(blobStore.getDefaultBucketName(), blobId, LOW_COST)) {
            FileUtils.copyInputStreamToFile(in, tempFile);
            uploadDocumentToTargetMail(mailAddress, tempFile);
        } finally {
            FileUtils.forceDelete(tempFile);
        }
    }

    private void uploadDocumentToTargetMail(MailAddress mailAddress, File tempFile) {
        User targetUser = linshareAPI.getUserByMail(mailAddress.asString());
        linshareAPI.uploadDocumentByDelegation(targetUser, tempFile);
    }
}
