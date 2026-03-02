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

package org.apache.james.jmap.vapid;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;

import jakarta.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.jmap.core.JmapRfc8621Configuration;
import org.apache.james.jwt.PublicKeyReader;

import com.google.common.base.Preconditions;

import nl.altindag.ssl.pem.util.PemUtils;

public class SecurityKeyLoader {
    private final FileSystem fileSystem;
    private final JmapRfc8621Configuration jmapRfc8621Configuration;

    @Inject
    SecurityKeyLoader(FileSystem fileSystem, JmapRfc8621Configuration jmapRfc8621Configuration) {
        this.fileSystem = fileSystem;
        this.jmapRfc8621Configuration = jmapRfc8621Configuration;
    }

    public AsymmetricKeys loadAsymmetricKeys() throws Exception {
        Preconditions.checkState(jmapRfc8621Configuration.vapidAuthEnabled(), "Vapid authentication is not enabled");
        Preconditions.checkState(jmapRfc8621Configuration.vapidPublicKey().isDefined());
        Preconditions.checkState(jmapRfc8621Configuration.vapidPrivateKey().isDefined());

        PrivateKey privateKey = PemUtils.loadPrivateKey(fileSystem.getResource(jmapRfc8621Configuration.vapidPrivateKey().get()));

        String publicKeyAsString = IOUtils.toString(fileSystem.getResource(jmapRfc8621Configuration.vapidPublicKey().get()), StandardCharsets.US_ASCII);
        PublicKey publicKey = new PublicKeyReader()
            .fromPEM(publicKeyAsString)
            .orElseThrow(() -> new IllegalArgumentException("Key must either be a valid certificate or a public key"));

        return new AsymmetricKeys(privateKey, publicKey);
    }
}
