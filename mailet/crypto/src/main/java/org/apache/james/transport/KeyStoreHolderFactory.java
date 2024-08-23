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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;

import jakarta.mail.MessagingException;

import org.apache.commons.io.input.UnsynchronizedBufferedInputStream;
import org.apache.james.filesystem.api.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

public class KeyStoreHolderFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyStoreHolderFactory.class);
    private static final String DEFAULT_KEYSTORE_FILE_PATH = FileSystem.FILE_PROTOCOL + System.getProperty("java.home") + "/lib/security/cacerts".replace('/', File.separatorChar);

    interface FileLoader {
        KeyStoreHolder load(KeyStoreHolderConfiguration config) throws Exception;
    }

    static class KeyStoreFileLoader implements FileLoader {
        private final FileSystem fileSystem;

        public KeyStoreFileLoader(FileSystem fileSystem) {
            this.fileSystem = fileSystem;
        }

        @Override
        public KeyStoreHolder load(KeyStoreHolderConfiguration config) {
            KeyStoreHolderConfiguration.KeyStoreConfiguration keyStoreConfig = (KeyStoreHolderConfiguration.KeyStoreConfiguration) config;
            return keyStoreConfig.getKeyStoreFileName()
                .map(Throwing.function(fileName -> createFromKeyStoreFile(fileName, keyStoreConfig.getKeyStorePassword(), keyStoreConfig.getKeyStoreType())))
                .orElseGet(Throwing.supplier(() -> {
                    LOGGER.info("No trusted store path specified, using default store.");
                    return createFromKeyStoreFile(DEFAULT_KEYSTORE_FILE_PATH, keyStoreConfig.getKeyStorePassword(), KeyStore.getDefaultType());
                }));
        }

        private KeyStoreHolder createFromKeyStoreFile(String keyStoreFileName, String keyStorePassword, String keyStoreType)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(UnsynchronizedBufferedInputStream
                .builder()
                .setInputStream(fileSystem.getResource(keyStoreFileName))
                .get(), keyStorePassword.toCharArray());
            if (keyStore.size() == 0) {
                throw new KeyStoreException("The keystore must be not empty");
            }
            return new KeyStoreHolder(keyStore);
        }
    }

    static class PemFileLoader implements FileLoader {
        private final FileSystem fileSystem;

        public PemFileLoader(FileSystem fileSystem) {
            this.fileSystem = fileSystem;
        }

        @Override
        public KeyStoreHolder load(KeyStoreHolderConfiguration config) throws Exception {
            KeyStoreHolderConfiguration.PemConfiguration pemConfig = (KeyStoreHolderConfiguration.PemConfiguration) config;
            KeyStore keyStore = PemReader.loadTrustStore(fileSystem.getFile(pemConfig.getPemFileName()));
            if (keyStore.size() == 0) {
                throw new KeyStoreException("The keystore must be not empty");
            }
            return new KeyStoreHolder(keyStore);
        }
    }

    public static KeyStoreHolderFactory from(FileSystem fileSystem) {
        return new KeyStoreHolderFactory(fileSystem);
    }

    private final FileSystem fileSystem;

    private KeyStoreHolderFactory(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public KeyStoreHolder createKeyStoreHolder(KeyStoreHolderConfiguration config) throws MessagingException {
        try {
            initJCE();
            return config.getFileLoader(fileSystem).load(config);
        } catch (Exception e) {
            throw new MessagingException("Error loading the trusted certificate store", e);
        }
    }

    private static void initJCE() throws NoSuchProviderException {
        try {
            InitJCE.init();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | InvocationTargetException | NoSuchMethodException e) {
            NoSuchProviderException ex = new NoSuchProviderException("Error during cryptography provider initialization. Has bcprov-jdkxx-yyy.jar been copied in the lib directory or installed in the system?");
            ex.initCause(e);
            throw ex;
        }
    }
}
