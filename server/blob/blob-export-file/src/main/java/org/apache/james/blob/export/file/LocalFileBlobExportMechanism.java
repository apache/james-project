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

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.MailetContext;

public class LocalFileBlobExportMechanism implements BlobExportMechanism {
    private static final int STRING_LENGTH = 32;
    private static final boolean WITH_LETTERS = true;
    private static final boolean WITH_NUMBERS = true;
    private static final String SUBJECT = "Some content had had just been exported";
    static final String CORRESPONDING_FILE_HEADER = "corresponding-file";

    public static class Configuration {
        private final String exportDirectory;

        public Configuration(String exportDirectory) {
            this.exportDirectory = exportDirectory;
        }
    }

    private final MailetContext mailetContext;
    private final BlobStore blobStore;
    private final FileSystem fileSystem;
    private final DNSService dnsService;
    private final Configuration configuration;

    LocalFileBlobExportMechanism(MailetContext mailetContext, BlobStore blobStore, FileSystem fileSystem, DNSService dnsService, Configuration configuration) {
        this.mailetContext = mailetContext;
        this.blobStore = blobStore;
        this.fileSystem = fileSystem;
        this.dnsService = dnsService;
        this.configuration = configuration;
    }

    @Override
    public ShareeStage blobId(BlobId blobId) {
        return mailAddress -> explanation -> () ->  {
            String fileUrl = copyBlobToFile(blobId);
            sendMail(mailAddress, fileUrl, explanation);
        };
    }

    private String copyBlobToFile(BlobId blobId) {
        try {
            File exportingDirectory = fileSystem.getFile(configuration.exportDirectory);
            FileUtils.forceMkdir(exportingDirectory);


            String fileName = RandomStringUtils.random(STRING_LENGTH, WITH_LETTERS, !WITH_NUMBERS);
            String fileURL = configuration.exportDirectory + "/" + fileName;
            File file = fileSystem.getFile(fileURL);
            FileUtils.copyToFile(blobStore.read(blobId), file);

            return fileURL;
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

            mailetContext.sendMail(mail);
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
