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

package org.apache.james.blob.export.api;

import java.util.Optional;

import org.apache.james.blob.api.BlobId;
import org.apache.james.core.MailAddress;

public interface BlobExportMechanism {

    class BlobExportException extends RuntimeException {
        public BlobExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @FunctionalInterface
    interface ShareeStage {
        ExplanationStage with(MailAddress sharee);
    }

    @FunctionalInterface
    interface ExplanationStage {
        FilePrefixStage explanation(String explanation);
    }

    @FunctionalInterface
    interface FilePrefixStage {
        FileExtensionStage filePrefix(Optional<String> prefix);

        default FileExtensionStage noFileCustomPrefix() {
            return filePrefix(Optional.empty());
        }

        default FileExtensionStage filePrefix(String prefix) {
            return filePrefix(Optional.of(prefix));
        }
    }

    @FunctionalInterface
    interface FileExtensionStage {
        FinalStage fileExtension(Optional<FileExtension> extension);

        default FinalStage noFileExtension() {
            return fileExtension(Optional.empty());
        }

        default FinalStage fileExtension(FileExtension extension) {
            return fileExtension(Optional.of(extension));
        }
    }

    @FunctionalInterface
    interface FinalStage {
        void export() throws BlobExportException;
    }

    ShareeStage blobId(BlobId blobId);
}
