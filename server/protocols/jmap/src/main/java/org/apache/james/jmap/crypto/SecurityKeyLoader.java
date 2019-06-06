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

package org.apache.james.jmap.crypto;

import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jmap.JMAPConfiguration;

import com.google.common.annotations.VisibleForTesting;

public class SecurityKeyLoader {

    private static final String ALIAS = "james";
    private static final String JKS = "JKS";

    private final FileSystem fileSystem;
    private final JMAPConfiguration jmapConfiguration;

    @VisibleForTesting
    @Inject
    SecurityKeyLoader(FileSystem fileSystem, JMAPConfiguration jmapConfiguration) {
        this.fileSystem = fileSystem;
        this.jmapConfiguration = jmapConfiguration;
    }

    public AsymmetricKeys load() throws Exception {
        KeyStore keystore = KeyStore.getInstance(JKS);
        InputStream fis = fileSystem.getResource(jmapConfiguration.getKeystore());
        char[] secret = jmapConfiguration.getSecret().toCharArray();
        keystore.load(fis, secret);
        Certificate aliasCertificate = Optional
                .ofNullable(keystore.getCertificate(ALIAS))
                .orElseThrow(() -> new KeyStoreException("Alias '" + ALIAS + "' keystore can't be found"));

        PublicKey publicKey = aliasCertificate.getPublicKey();
        Key key = keystore.getKey(ALIAS, secret);
        if (! (key instanceof PrivateKey)) {
            throw new KeyStoreException("Provided key is not a PrivateKey");
        }
        return new AsymmetricKeys((PrivateKey) key, publicKey);
    }
}
