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

package org.apache.james.blob.export.file;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.blob.export.api.ExportedFileNamesGenerator;
import org.apache.james.blob.export.api.FileExtension;
import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.MailetContext;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class LocalFileBlobExportMechanism implements BlobExportMechanism {
    private static final String SUBJECT = "Some content had had just been exported";
    static final String CORRESPONDING_FILE_HEADER = "corresponding-file";

    public static class Configuration {

        public static Optional<Configuration> from(org.apache.commons.configuration2.Configuration propertiesConfiguration) {
            String exportDirectory = propertiesConfiguration.getString(DIRECTORY_LOCATION_PROPERTY, null);
            return Optional.ofNullable(exportDirectory).map(Configuration::new);
        }

        private static final String DIRECTORY_LOCATION_PROPERTY = "blob.export.localFile.directory";
        private static final String DEFAULT_DIRECTORY_LOCATION = "file://var/blobExporting";
        public static final Configuration DEFAULT_CONFIGURATION = new Configuration(DEFAULT_DIRECTORY_LOCATION);

        private final String exportDirectory;

        @VisibleForTesting
        Configuration(String exportDirectory) {
            Preconditions.checkNotNull(exportDirectory);

            this.exportDirectory = exportDirectory;
        }

        public String getExportDirectory() {
            return exportDirectory;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Configuration) {
                Configuration that = (Configuration) o;

                return Objects.equals(this.exportDirectory, that.exportDirectory);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(exportDirectory);
        }
    }

    private final MailetContext mailetContext;
    private final BlobStore blobStore;
    private final FileSystem fileSystem;
    private final DNSService dnsService;
    private final Configuration configuration;

    @VisibleForTesting
    @Inject
    public LocalFileBlobExportMechanism(MailetContext mailetContext, BlobStore blobStore, FileSystem fileSystem, DNSService dnsService, Configuration configuration) {
        this.mailetContext = mailetContext;
        this.blobStore = blobStore;
        this.fileSystem = fileSystem;
        this.dnsService = dnsService;
        this.configuration = configuration;
    }

    @Override
    public ShareeStage blobId(BlobId blobId) {
        return mailAddress -> explanation -> fileCustomPrefix -> fileExtension -> () ->  {
            String fileUrl = copyBlobToFile(blobId, fileCustomPrefix, fileExtension);
            sendMail(mailAddress, fileUrl, explanation);
        };
    }

    private String copyBlobToFile(BlobId blobId,
                                  Optional<String> fileCustomPrefix,
                                  Optional<FileExtension> fileExtension) {
        try {
            File exportingDirectory = fileSystem.getFile(configuration.exportDirectory);
            FileUtils.forceMkdir(exportingDirectory);

            String fileName = ExportedFileNamesGenerator.generateFileName(fileCustomPrefix, blobId, fileExtension);
            String fileURL = configuration.exportDirectory + "/" + fileName;
            File file = fileSystem.getFile(fileURL);
            try (InputStream in = blobStore.read(blobStore.getDefaultBucketName(), blobId, LOW_COST)) {
                FileUtils.copyToFile(in, file);
            }

            return file.getAbsolutePath();
        } catch (IOException e) {
            throw new BlobExportException("Error while copying blob to file", e);
        }
    }

    private void sendMail(MailAddress mailAddress, String fileUrl, String explanation) {
        try {
            MimeMessageBuilder mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(CORRESPONDING_FILE_HEADER, fileUrl)
                .addFrom(mailetContext.getPostmaster().asString())
                .addToRecipient(mailAddress.asString())
                .setSubject(SUBJECT)
                .setText(computeMessage(explanation, fileUrl));

            MailImpl mail = MailImpl.builder()
                .name(MailImpl.getId())
                .sender(mailetContext.getPostmaster())
                .addRecipient(mailAddress)
                .mimeMessage(mimeMessage)
                .build();

            try {
                mailetContext.sendMail(mail);
            } finally {
                LifecycleUtil.dispose(mail);
            }
        } catch (Exception e) {
            throw new BlobExportException("Error while sending email", e);
        }
    }

    private String computeMessage(String explanationText, String fileUrl) throws UnknownHostException {
        String hostname = dnsService.getLocalHost().getHostName();
        return explanationText + "\n\n" +
            "The content of this blob can be read directly on James host filesystem (" + hostname + ") in this file: " + fileUrl;
    }
}
