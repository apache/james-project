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
package org.apache.james.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

public class MimeMessageUtil {

    public static String asString(MimeMessage mimeMessage) throws Exception {
        return new String(asBytes(mimeMessage), StandardCharsets.UTF_8);
    }

    public static byte[] asBytes(MimeMessage mimeMessage) throws Exception {
        UnsynchronizedByteArrayOutputStream byteArrayOutputStream = new UnsynchronizedByteArrayOutputStream();
        mimeMessage.writeTo(byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

    public static MimeMessage defaultMimeMessage() {
        return new MimeMessage(Session.getDefaultInstance(new Properties()));
    }

    public static MimeMessage mimeMessageFromStream(InputStream inputStream) throws MessagingException {
        return new MimeMessage(Session.getDefaultInstance(new Properties()), inputStream);
    }

    public static MimeMessage mimeMessageFromBytes(byte[] bytes) throws MessagingException {
        return mimeMessageFromStream(new ByteArrayInputStream(bytes));
    }

    public static MimeMessage mimeMessageFromString(String string) throws MessagingException {
        return mimeMessageFromBytes(string.getBytes(StandardCharsets.UTF_8));
    }

}
