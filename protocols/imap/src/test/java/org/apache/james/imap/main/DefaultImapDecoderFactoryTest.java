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

package org.apache.james.imap.main;

import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultImapDecoderFactoryTest {

    @Test
    void createDefaultImapDecoderFactoryWithDefaultConstructor() {
        // Create an instance using the default constructor
        DefaultImapDecoderFactory factory = new DefaultImapDecoderFactory();

        // Assert that the factory is not null
        assertNotNull(factory, "Factory instance should not be null");

        // Build an ImapDecoder using the factory and assert it's not null
        ImapDecoder decoder = factory.buildImapDecoder();
        assertNotNull(decoder, "ImapDecoder instance should not be null");
    }

    @Test
    void createDefaultImapDecoderFactoryWithCustomStatusResponseFactory() {
        // Create a custom StatusResponseFactory
        StatusResponseFactory customStatusResponseFactory = new UnpooledStatusResponseFactory();

        // Create an instance using the custom StatusResponseFactory
        DefaultImapDecoderFactory factory = new DefaultImapDecoderFactory(customStatusResponseFactory);

        // Assert that the factory is not null
        assertNotNull(factory, "Factory instance should not be null");

        // Build an ImapDecoder using the factory and assert it's not null
        ImapDecoder decoder = factory.buildImapDecoder();
        assertNotNull(decoder, "ImapDecoder instance should not be null");
    }
}
