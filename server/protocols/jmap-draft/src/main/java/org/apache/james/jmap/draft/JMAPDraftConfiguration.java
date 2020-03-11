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

import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class JMAPDraftConfiguration {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String keystore;
        private String secret;
        private Optional<Boolean> enabled = Optional.empty();
        private Optional<String> jwtPublicKeyPem = Optional.empty();

        private Builder() {

        }

        public Builder keystore(String keystore) {
            this.keystore = keystore;
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
            this.secret = secret;
            return this;
        }

        public Builder jwtPublicKeyPem(Optional<String> jwtPublicKeyPem) {
            Preconditions.checkNotNull(jwtPublicKeyPem);
            this.jwtPublicKeyPem = jwtPublicKeyPem;
            return this;
        }

        public JMAPDraftConfiguration build() {
            Preconditions.checkState(enabled.isPresent(), "You should specify if JMAP server should be started");
            Preconditions.checkState(!enabled.get() || !Strings.isNullOrEmpty(keystore), "'keystore' is mandatory");
            Preconditions.checkState(!enabled.get() || !Strings.isNullOrEmpty(secret), "'secret' is mandatory");
            Preconditions.checkState(!enabled.get() || jwtPublicKeyPem.isPresent(), "'publicKey' is mandatory");
            return new JMAPDraftConfiguration(enabled.get(), keystore, secret, jwtPublicKeyPem);
        }

    }

    private final boolean enabled;
    private final String keystore;
    private final String secret;
    private final Optional<String> jwtPublicKeyPem;

    @VisibleForTesting
    JMAPDraftConfiguration(boolean enabled, String keystore, String secret, Optional<String> jwtPublicKeyPem) {
        this.enabled = enabled;
        this.keystore = keystore;
        this.secret = secret;
        this.jwtPublicKeyPem = jwtPublicKeyPem;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getKeystore() {
        return keystore;
    }

    public String getSecret() {
        return secret;
    }

    public Optional<String> getJwtPublicKeyPem() {
        return jwtPublicKeyPem;
    }
}
