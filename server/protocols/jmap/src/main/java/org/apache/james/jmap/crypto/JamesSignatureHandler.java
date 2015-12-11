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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lifecycle.api.Configurable;

import com.google.common.base.Preconditions;

public class JamesSignatureHandler implements SignatureHandler, Configurable {

    public static final String ALIAS = "james";
    public static final String ALGORITHM = "SHA1withRSA";
    public static final String JKS = "JKS";
    
    private final FileSystem fileSystem;
    private String secret;
    private String keystoreURL;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    public JamesSignatureHandler(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public void configure(HierarchicalConfiguration configuration) throws ConfigurationException {
        keystoreURL = configuration.getString("tls.keystoreURL", "file://conf/keystoreURL");
        secret = configuration.getString("tls.secret", "");
    }

    public void init() throws Exception {
        KeyStore keystore = KeyStore.getInstance(JKS);
        InputStream fis = fileSystem.getResource(keystoreURL);
        keystore.load(fis, secret.toCharArray());
        publicKey = keystore.getCertificate(ALIAS).getPublicKey();
        Key key = keystore.getKey(ALIAS, secret.toCharArray());
        if (! (key instanceof PrivateKey)) {
            throw new Exception("Provided key is not a PrivateKey");
        }
        privateKey = (PrivateKey) key;
    }

    @Override
    public String sign(String source) throws Exception {
        Preconditions.checkNotNull(source);
        Signature javaSignature = Signature.getInstance(ALGORITHM);
        javaSignature.initSign(privateKey);
        javaSignature.update(source.getBytes());
        return new Base64().encodeAsString(javaSignature.sign());
    }

    @Override
    public boolean verify(String source, String signature) throws Exception {
        Preconditions.checkNotNull(source);
        Preconditions.checkNotNull(signature);
        Signature javaSignature = Signature.getInstance(ALGORITHM);
        javaSignature.initVerify(publicKey);
        javaSignature.update(source.getBytes());
        return javaSignature.verify(new Base64().decode(signature));
    }
}
