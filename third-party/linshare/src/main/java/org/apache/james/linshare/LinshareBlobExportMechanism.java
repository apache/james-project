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

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.blob.export.api.FileExtension;
import org.apache.james.core.MailAddress;
import org.apache.james.linshare.client.Document;
import org.apache.james.linshare.client.LinshareAPI;
import org.apache.james.linshare.client.ShareRequest;

public class LinshareBlobExportMechanism implements BlobExportMechanism {
    private final LinshareAPI linshareAPI;
    private final BlobStore blobStore;

    @Inject
    LinshareBlobExportMechanism(LinshareAPI linshareAPI, BlobStore blobStore) {
        this.linshareAPI = linshareAPI;
        this.blobStore = blobStore;
    }

    @Override
    public ShareeStage blobId(BlobId blobId) {
        return mailAddress -> explanation -> fileExtension -> () ->  {
            try {
                exportBlob(blobId, mailAddress, fileExtension);
            } catch (Exception e) {
                throw new BlobExportException("Error while exporting blob " + blobId.asString() + " to " + mailAddress.asString(), e);
            }
        };
    }

    private void exportBlob(BlobId blobId, MailAddress mailAddress, Optional<FileExtension> fileExtension) throws IOException {
        File tempFile = createTempFile(blobId, fileExtension);
        try {
            FileUtils.copyInputStreamToFile(blobStore.read(blobId), tempFile);
            uploadAndShare(mailAddress, tempFile);
        } finally {
            FileUtils.forceDelete(tempFile);
        }
    }

    private void uploadAndShare(MailAddress mailAddress, File tempFile) {
        Document document = linshareAPI.uploadDocument(tempFile);
        linshareAPI.share(ShareRequest.builder()
            .addDocumentId(document.getId())
            .addRecipient(mailAddress)
            .build());
    }

    private File createTempFile(BlobId blobId, Optional<FileExtension> fileExtension) throws IOException {
        String suffix = fileExtension.map(FileExtension::asFileSuffix).orElse("");
        return File.createTempFile(blobId.asString() + "_", suffix);
    }
}
