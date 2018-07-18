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

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyStoreException;

import org.apache.james.jmap.JMAPConfiguration;
import org.junit.Before;
import org.junit.Test;

public class JamesSignatureHandlerTest {

    public static final String SIGNATURE = "NeIFNei4p6vn085wCEw0pbEwJ+Oak5yEIRLZsDcRVzT9rWWOcLvDFUA3S6awi/bxPiFxqJFreVz6xqzehnUI4tUBupk3sIsqeXShhFWBpaV+m58mC41lT/A0RJa3GgCvg6kmweCRf3tOo0+gvwOQJdwCL2B21GjDCKqBHaiK+OHcsSjrQW0xuew5z84EAz3ErdH4MMNjITksxK5FG/cGQ9V6LQgwcPk0RrprVC4eY7FFHw/sQNlJpZKsSFLnn5igPQkQtjiQ4ay1/xoB7FU7aJLakxRhYOnTKgper/Ur7UWOZJaE+4EjcLwCFLF9GaCILwp9W+mf/f7j92PVEU50Vg==";
    private static final String FAKE_SIGNATURE = "MeIFNei4p6vn085wCEw0pbEwJ+Oak5yEIRLZsDcRVzT9rWWOcLvDFUA3S6awi/bxPiFxqJFreVz6xqzehnUI4tUBupk3sIsqeXShhFWBpaV+m58mC41lT/A0RJa3GgCvg6kmweCRf3tOo0+gvwOQJdwCL2B21GjDCKqBHaiK+OHcsSjrQW0xuew5z84EAz3ErdH4MMNjITksxK5FG/cGQ9V6LQgwcPk0RrprVC4eY7FFHw/sQNlJpZKsSFLnn5igPQkQtjiQ4ay1/xoB7FU7aJLakxRhYOnTKgper/Ur7UWOZJaE+4EjcLwCFLF9GaCILwp9W+mf/f7j92PVEU50Vg==";
    public static final String SOURCE = "plop";

    private JamesSignatureHandler signatureHandler;

    @Before
    public void setUp() throws Exception {
       signatureHandler = new JamesSignatureHandlerProvider().provide();
    }

    @Test(expected = KeyStoreException.class)
    public void initShouldThrowOnUnknownKeystore() throws Exception {
        JMAPConfiguration jmapConfiguration = JamesSignatureHandlerProvider.newConfigurationBuilder()
            .keystore("badAliasKeystore")
            .secret("password")
            .build();

        JamesSignatureHandler signatureHandler = new JamesSignatureHandler(JamesSignatureHandlerProvider.newFileSystem(),
                jmapConfiguration);
        signatureHandler.init();
    }

    @Test
    public void validSignatureShouldBeRecognised() throws Exception {
        assertThat(signatureHandler.verify(SOURCE, signatureHandler.sign(SOURCE))).isTrue();
    }

    @Test
    public void invalidSignatureShouldNotBeRecognised() throws Exception {
        assertThat(signatureHandler.verify(SOURCE, signatureHandler.sign(FAKE_SIGNATURE))).isFalse();
    }

    @Test
    public void incorrectLengthSignatureShouldReturnFalse() throws Exception {
        assertThat(signatureHandler.verify(SOURCE, "c2lnbmF0dXJl")).isFalse();
    }

    @Test(expected = NullPointerException.class)
    public void signShouldThrowOnNullSource() throws Exception {
        signatureHandler.sign(null);
    }

    @Test(expected = NullPointerException.class)
    public void verifyShouldThrowOnNullSource() throws Exception {
        signatureHandler.verify(null, "signature");
    }

    @Test(expected = NullPointerException.class)
    public void verifyShouldThrowOnNullSignature() throws Exception {
        signatureHandler.verify(SOURCE, null);
    }

    @Test
    public void signOutputShouldBeValid() throws Exception {
        assertThat(signatureHandler.sign(SOURCE))
            .isEqualTo(SIGNATURE);
    }

    @Test
    public void verifyOutputShouldBeValid() throws Exception {
        assertThat(signatureHandler.verify(SOURCE,
            SIGNATURE))
            .isTrue();
    }

}
