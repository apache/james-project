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

package org.apache.james.webadmin;

import java.util.Objects;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class TlsConfiguration {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String keystoreFilePath;
        private String keystorePassword;
        private String certificatesFilePath;
        private String privateKeyFilePath;
        private String privateKeyPassword;
        private String truststoreFilePath;
        private String truststorePassword;

        public Builder raw(String keystoreFilePath,
                           String keystorePassword,
                           String truststoreFilePath,
                           String truststorePassword) {
            Preconditions.checkNotNull(keystoreFilePath);
            Preconditions.checkNotNull(keystorePassword);

            this.keystoreFilePath = keystoreFilePath;
            this.keystorePassword = keystorePassword;
            this.truststoreFilePath = truststoreFilePath;
            this.truststorePassword = truststorePassword;
            return this;
        }

        public Builder pem(String certificatesFilePath,
                           String privateKeyFilePath,
                           String privateKeyPassword,
                           String truststoreFilePath,
                           String truststorePassword) {
            Preconditions.checkNotNull(certificatesFilePath);
            Preconditions.checkNotNull(privateKeyFilePath);

            this.certificatesFilePath = certificatesFilePath;
            this.privateKeyFilePath = privateKeyFilePath;
            this.privateKeyPassword = privateKeyPassword;
            this.truststoreFilePath = truststoreFilePath;
            this.truststorePassword = truststorePassword;
            return this;
        }

        public Builder pem(String certificatesFilePath,
                           String privateKeyFilePath,
                           String privateKeyPassword) {
            return pem(certificatesFilePath, privateKeyFilePath, privateKeyPassword, null, null);
        }

        public Builder pem(String certificatesFilePath,
                           String privateKeyFilePath) {
            return pem(certificatesFilePath, privateKeyFilePath, null, null, null);
        }

        public Builder selfSigned(String keystoreFilePath, String keystorePassword) {
            Preconditions.checkNotNull(keystoreFilePath);
            Preconditions.checkNotNull(keystorePassword);

            this.keystoreFilePath = keystoreFilePath;
            this.keystorePassword = keystorePassword;
            return this;
        }

        public TlsConfiguration build() {
            Preconditions.checkState(hasKeystoreInformation() || hasPemInformation(),
                "If enabled, you need to provide keystore information or (certificates and privateKey)");
            Preconditions.checkState(optionalHasTrustStoreInformation(), "You need to provide both information about trustStore");
            return new TlsConfiguration(keystoreFilePath, keystorePassword, certificatesFilePath, privateKeyFilePath, privateKeyPassword, truststoreFilePath, truststorePassword);
        }

        private boolean optionalHasTrustStoreInformation() {
            return (truststoreFilePath == null) == (truststorePassword == null);
        }

        private boolean hasKeystoreInformation() {
            return keystorePassword != null && keystoreFilePath != null;
        }

        private boolean hasPemInformation() {
            return certificatesFilePath != null && privateKeyFilePath != null;
        }

    }

    private final String keystoreFilePath;
    private final String keystorePassword;
    private final String certificatesFilePath;
    private final String privateKeyFilePath;
    private final String privateKeyPassword;
    private final String truststoreFilePath;
    private final String truststorePassword;

    @VisibleForTesting
    TlsConfiguration(String keystoreFilePath, String keystorePassword, String truststoreFilePath, String truststorePassword) {
        this(keystoreFilePath, keystorePassword, null, null, null, truststoreFilePath, truststorePassword);
    }

    @VisibleForTesting
    TlsConfiguration(String keystoreFilePath, String keystorePassword,
                     String certificatesFilePath, String privateKeyFilePath, String privateKeyPassword,
                     String truststoreFilePath, String truststorePassword) {
        this.keystoreFilePath = keystoreFilePath;
        this.keystorePassword = keystorePassword;
        this.certificatesFilePath = certificatesFilePath;
        this.privateKeyFilePath = privateKeyFilePath;
        this.privateKeyPassword = privateKeyPassword;
        this.truststoreFilePath = truststoreFilePath;
        this.truststorePassword = truststorePassword;
    }

    public String getKeystoreFilePath() {
        return keystoreFilePath;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public String getCertificatesFilePath() {
        return certificatesFilePath;
    }

    public String getPrivateKeyFilePath() {
        return privateKeyFilePath;
    }

    public String getPrivateKeyPassword() {
        return privateKeyPassword;
    }

    public boolean isPem() {
        return certificatesFilePath != null && privateKeyFilePath != null;
    }

    public String getTruststoreFilePath() {
        return truststoreFilePath;
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    @Override
    public final boolean equals(Object o) {
       if (o instanceof TlsConfiguration) {
           TlsConfiguration that = (TlsConfiguration) o;

           return Objects.equals(this.keystoreFilePath, that.keystoreFilePath)
               && Objects.equals(this.keystorePassword, that.keystorePassword)
               && Objects.equals(this.certificatesFilePath, that.certificatesFilePath)
               && Objects.equals(this.privateKeyFilePath, that.privateKeyFilePath)
               && Objects.equals(this.privateKeyPassword, that.privateKeyPassword)
               && Objects.equals(this.truststoreFilePath, that.truststoreFilePath)
               && Objects.equals(this.truststorePassword, that.truststorePassword);
       }
       return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(keystoreFilePath, keystorePassword, certificatesFilePath, privateKeyFilePath, privateKeyPassword, truststoreFilePath, truststorePassword);
    }
}
