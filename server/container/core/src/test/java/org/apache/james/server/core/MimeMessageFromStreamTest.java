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

package org.apache.james.server.core;

import java.io.ByteArrayInputStream;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

public class MimeMessageFromStreamTest extends MimeMessageTest {

    protected MimeMessage getMessageFromSources(String sources) throws Exception {
        return new MimeMessage(Session.getDefaultInstance(new Properties()), new ByteArrayInputStream(sources.getBytes()));
    }

    @Override
    protected MimeMessage getMultipartMessage() throws Exception {
        return getMessageFromSources(getMultipartMessageSource());
    }

    @Override
    protected MimeMessage getSimpleMessage() throws Exception {
        return getMessageFromSources(getSimpleMessageCleanedSource());
    }

    @Override
    protected MimeMessage getMessageWithBadReturnPath() throws Exception {
        return getMessageFromSources(getMessageWithBadReturnPathSource());
    }

    @Override
    protected MimeMessage getMissingEncodingMessage() throws Exception {
        return getMessageFromSources(getMissingEncodingMessageSource());
    }

}
