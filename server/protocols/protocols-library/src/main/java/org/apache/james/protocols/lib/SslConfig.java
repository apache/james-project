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

package org.apache.james.protocols.lib;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.protocols.api.ClientAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SslConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(SslConfig.class);

    public static SslConfig parse(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
        boolean useStartTLS = config.getBoolean("tls.[@startTLS]", false);
        boolean useSSL = config.getBoolean("tls.[@socketTLS]", false);

        ClientAuth clientAuth;
        if (config.getProperty("tls.clientAuth") != null || config.getKeys("tls.clientAuth").hasNext()) {
            clientAuth = ClientAuth.NEED;
        } else {
            clientAuth = ClientAuth.NONE;
        }

        if (useSSL && useStartTLS) {
            throw new ConfigurationException("startTLS is only supported when using plain sockets");
        }

        if (useStartTLS || useSSL) {
            String[] enabledCipherSuites = config.getStringArray("tls.supportedCipherSuites.cipherSuite");
            String keystore = config.getString("tls.keystore", null);
            String privateKey = config.getString("tls.privateKey", null);
            String certificates = config.getString("tls.certificates", null);
            String keystoreType = config.getString("tls.keystoreType", "JKS");
            if (keystore == null && (privateKey == null || certificates == null)) {
                throw new ConfigurationException("keystore or (privateKey and certificates) needs to get configured");
            }
            String secret = config.getString("tls.secret", null);

            String truststore = config.getString("tls.clientAuth.truststore", null);
            String truststoreType = config.getString("tls.clientAuth.truststoreType", "JKS");
            char[] truststoreSecret = config.getString("tls.clientAuth.truststoreSecret", "").toCharArray();
            LOGGER.info("TLS enabled with auth {} using truststore {}", clientAuth, truststore);

            return new SslConfig(useStartTLS, useSSL, clientAuth, keystore, keystoreType, privateKey, certificates, secret, truststore, truststoreType, enabledCipherSuites, truststoreSecret);
        } else {
            return new SslConfig(useStartTLS, useSSL, clientAuth, null, null, null, null, null, null, null, null, null);
        }
    }

    private final boolean useStartTLS;
    private final boolean useSSL;
    private final ClientAuth clientAuth;
    private final String keystore;
    private final String keystoreType;
    private final String privateKey;
    private final String certificates;
    private final String secret;
    private final String truststore;
    private final String truststoreType;
    private final String[] enabledCipherSuites;
    private final char[] truststoreSecret;

    public SslConfig(boolean useStartTLS, boolean useSSL, ClientAuth clientAuth, String keystore, String keystoreType, String privateKey,
                     String certificates, String secret, String truststore, String truststoreType, String[] enabledCipherSuites, char[] truststoreSecret) {
        this.useStartTLS = useStartTLS;
        this.useSSL = useSSL;
        this.clientAuth = clientAuth;
        this.keystore = keystore;
        this.keystoreType = keystoreType;
        this.privateKey = privateKey;
        this.certificates = certificates;
        this.secret = secret;
        this.truststore = truststore;
        this.truststoreType = truststoreType;
        this.enabledCipherSuites = enabledCipherSuites;
        this.truststoreSecret = truststoreSecret;
    }

    public ClientAuth getClientAuth() {
        return clientAuth;
    }

    public boolean useStartTLS() {
        return useStartTLS;
    }

    public String[] getEnabledCipherSuites() {
        return enabledCipherSuites;
    }

    public boolean useSSL() {
        return useSSL;
    }

    public String getKeystore() {
        return keystore;
    }

    public String getKeystoreType() {
        return keystoreType;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public String getCertificates() {
        return certificates;
    }

    public String getSecret() {
        return secret;
    }

    public String getTruststore() {
        return truststore;
    }

    public String getTruststoreType() {
        return truststoreType;
    }

    public char[] getTruststoreSecret() {
        return truststoreSecret;
    }
}
