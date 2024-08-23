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

package org.apache.james.transport;

import java.security.KeyStore;
import java.util.Optional;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.mailet.MailetConfig;

import com.google.common.base.Preconditions;

public abstract class KeyStoreHolderConfiguration {
    public static final String FILE_TYPE = "fileType";
    public static final String KEY_STORE_TYPE = "keyStoreType";
    public static final String KEY_STORE_FILE_NAME = "keyStoreFileName";
    public static final String KEY_STORE_PASSWORD = "keyStorePassword";
    public static final String PEM_FILE_NAME = "pemFileName";
    public static final String KEY_STORE_TYPE_DEFAULT_VALUE = KeyStore.getDefaultType();
    public static final String KEY_STORE_PASSWORD_DEFAULT_VALUE = "";

    public static class Builder {
        private KeyFileType fileType;
        private Optional<String> keyStoreType;
        private Optional<String> keyStoreFileName;
        private Optional<String> keyStorePassword;
        private Optional<String> pemFileName;

        public Builder setFileType(KeyFileType fileType) {
            this.fileType = fileType;
            return this;
        }

        public Builder setKeyStoreType(Optional<String> keyStoreType) {
            this.keyStoreType = keyStoreType;
            return this;
        }

        public Builder setKeyStoreFileName(Optional<String> keyStoreFileName) {
            this.keyStoreFileName = keyStoreFileName;
            return this;
        }

        public Builder setKeyStorePassword(Optional<String> keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
            return this;
        }

        public Builder setPemFileName(Optional<String> pemFileName) {
            this.pemFileName = pemFileName;
            return this;
        }

        public KeyStoreHolderConfiguration build() {
            switch (this.fileType) {
                case KEYSTORE:
                    return new KeyStoreConfiguration(keyStoreType.orElse(KEY_STORE_TYPE_DEFAULT_VALUE),
                        keyStoreFileName,
                        keyStorePassword.orElse(KEY_STORE_PASSWORD_DEFAULT_VALUE));
                case PEM:
                    Preconditions.checkArgument(pemFileName.isPresent() && !pemFileName.get().isBlank(), "pemFileName must not be empty");
                    return new PemConfiguration(pemFileName.get());
                default: throw new RuntimeException("Unsupported file type " + this.fileType);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static KeyStoreHolderConfiguration from(MailetConfig config) {
        return builder().setFileType(KeyFileType.parse(Optional.ofNullable(config.getInitParameter(FILE_TYPE))))
            .setKeyStoreType(Optional.ofNullable(config.getInitParameter(KEY_STORE_TYPE)))
            .setKeyStoreFileName(Optional.ofNullable(config.getInitParameter(KEY_STORE_FILE_NAME)))
            .setKeyStorePassword(Optional.ofNullable(config.getInitParameter(KEY_STORE_PASSWORD)))
            .setPemFileName(Optional.ofNullable(config.getInitParameter(PEM_FILE_NAME)))
            .build();
    }

    public static class KeyStoreConfiguration extends KeyStoreHolderConfiguration {
        private final String keyStoreType;
        private final Optional<String> keyStoreFileName;
        private final String keyStorePassword;

        private KeyStoreConfiguration(String keyStoreType, Optional<String> keyStoreFileName, String keyStorePassword) {
            this.keyStoreType = keyStoreType;
            this.keyStoreFileName = keyStoreFileName;
            this.keyStorePassword = keyStorePassword;
        }

        public String getKeyStoreType() {
            return keyStoreType;
        }

        public Optional<String> getKeyStoreFileName() {
            return keyStoreFileName;
        }

        public String getKeyStorePassword() {
            return keyStorePassword;
        }

        @Override
        public KeyStoreHolderFactory.FileLoader getFileLoader(FileSystem fileSystem) {
            return new KeyStoreHolderFactory.KeyStoreFileLoader(fileSystem);
        }
    }

    public static class PemConfiguration extends KeyStoreHolderConfiguration {
        private final String pemFileName;

        private PemConfiguration(String pemFileName) {
            this.pemFileName = pemFileName;
        }

        public String getPemFileName() {
            return pemFileName;
        }

        @Override
        public KeyStoreHolderFactory.FileLoader getFileLoader(FileSystem fileSystem) {
            return new KeyStoreHolderFactory.PemFileLoader(fileSystem);
        }
    }

    public abstract KeyStoreHolderFactory.FileLoader getFileLoader(FileSystem fileSystem);
}
