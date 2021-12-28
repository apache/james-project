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
package org.apache.james.jmap.draft;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class JMAPDraftConfiguration {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<String> keystore = Optional.empty();
        private Optional<String> keystoreType = Optional.empty();
        private Optional<String> privateKey = Optional.empty();
        private Optional<String> certificates = Optional.empty();
        private Optional<String> secret = Optional.empty();
        private Optional<Boolean> enabled = Optional.empty();
        private ImmutableList.Builder<String> jwtPublicKeyPem = ImmutableList.builder();
        private Optional<List<String>> authenticationStrategies = Optional.empty();

        private Builder() {

        }

        public Builder keystore(String keystore) {
            this.keystore = Optional.ofNullable(keystore)
                .filter(s -> !s.isEmpty());
            return this;
        }

        public Builder privateKey(String privateKey) {
            this.privateKey = Optional.ofNullable(privateKey);
            return this;
        }

        public Builder certificates(String certificates) {
            this.certificates = Optional.ofNullable(certificates);
            return this;
        }

        public Builder keystoreType(String keystoreType) {
            this.keystoreType = Optional.ofNullable(keystoreType);
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = Optional.of(enabled);
            return this;
        }

        public Builder enable() {
            return enabled(true);
        }

        public Builder disable() {
            return enabled(false);
        }

        public Builder secret(String secret) {
            this.secret = Optional.ofNullable(secret)
                .filter(s -> !s.isEmpty());
            return this;
        }

        public Builder jwtPublicKeyPem(Collection<String> jwtPublicKeyPem) {
            Preconditions.checkNotNull(jwtPublicKeyPem);
            this.jwtPublicKeyPem.addAll(jwtPublicKeyPem);
            return this;
        }

        public Builder authenticationStrategies(Optional<List<String>> authenticationStrategies) {
            this.authenticationStrategies = authenticationStrategies;
            return this;
        }

        public JMAPDraftConfiguration build() {
            Preconditions.checkState(enabled.isPresent(), "You should specify if JMAP server should be started");

            Preconditions.checkState(!enabled.get() || cryptoParametersAreSpecified(),
                "('keystore' && 'secret') or (privateKey && certificates) is mandatory");
            return new JMAPDraftConfiguration(enabled.get(), keystore, privateKey, certificates, keystoreType.orElse("JKS"),
                secret, jwtPublicKeyPem.build(), authenticationStrategies);
        }

        private boolean cryptoParametersAreSpecified() {
            return (keystore.isPresent() && secret.isPresent())
                || (privateKey.isPresent() && certificates.isPresent());
        }
    }

    private final boolean enabled;
    private final Optional<String> keystore;
    private final Optional<String> privateKey;
    private final Optional<String> certificates;
    private final String keystoreType;
    private final Optional<String> secret;
    private final List<String> jwtPublicKeyPem;
    private final Optional<List<String>> authenticationStrategies;

    @VisibleForTesting
    JMAPDraftConfiguration(boolean enabled, Optional<String> keystore, Optional<String> privateKey, Optional<String> certificates, String keystoreType,
                           Optional<String> secret, List<String> jwtPublicKeyPem, Optional<List<String>> authenticationStrategies) {
        this.enabled = enabled;
        this.keystore = keystore;
        this.privateKey = privateKey;
        this.certificates = certificates;
        this.keystoreType = keystoreType;
        this.secret = secret;
        this.jwtPublicKeyPem = jwtPublicKeyPem;
        this.authenticationStrategies = authenticationStrategies;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<String> getKeystore() {
        return keystore;
    }

    public String getKeystoreType() {
        return keystoreType;
    }

    public Optional<String> getPrivateKey() {
        return privateKey;
    }

    public Optional<String> getCertificates() {
        return certificates;
    }

    public Optional<String> getSecret() {
        return secret;
    }

    public List<String> getJwtPublicKeyPem() {
        return jwtPublicKeyPem;
    }

    public Optional<List<String>> getAuthenticationStrategies() {
        return authenticationStrategies;
    }
}
