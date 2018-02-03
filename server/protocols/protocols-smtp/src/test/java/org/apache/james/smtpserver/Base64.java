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
package org.apache.james.smtpserver;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.mail.MessagingException;
import javax.mail.internet.MimeUtility;

/**
 * Performs simple Base64 encoding and decode suitable for authentication. Note
 * that this is not a general purpose codec.
 */
public class Base64 {

    /**
     * Decode base64 encoded String
     * 
     * @param b64string
     *            base64 String
     * @return reader the BufferedReader which holds the decoded base64 text
     * @throws MessagingException
     *             get thrown when an error was detected while trying to decode
     *             the String
     */
    public static BufferedReader decode(String b64string) throws MessagingException {
        return new BufferedReader(new InputStreamReader(MimeUtility.decode(
                new ByteArrayInputStream(b64string.getBytes()), "base64")));
    }

    /**
     * Decode base64 encoded String
     * 
     * @param b64string
     *            base64 Sting
     * @return returnString the String which holds the docoded base64 text
     * @throws MessagingException
     *             get thrown when an error was detected while trying to decode
     *             the String
     * @throws IOException
     *             get thrown when I/O error was detected
     */
    public static String decodeAsString(String b64string) throws IOException, MessagingException {
        if (b64string == null) {
            return b64string;
        }
        String returnString = decode(b64string).readLine();
        if (returnString == null) {
            return returnString;
        }
        return returnString.trim();
    }

    /**
     * Encode String to base64
     * 
     * @param plaintext
     *            the plaintext to encode
     * @return out the ByteArrayOutputStream holding the encoded given text
     * @throws IOException
     *             get thrown when I/O error was detected
     * @throws MessagingException
     *             get thrown when an error was detected while trying to encode
     *             the String
     */
    public static ByteArrayOutputStream encode(String plaintext) throws IOException, MessagingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] in = plaintext.getBytes();
        ByteArrayOutputStream inStream = new ByteArrayOutputStream();
        inStream.write(in, 0, in.length);
        // pad
        if ((in.length % 3) == 1) {
            inStream.write(0);
            inStream.write(0);
        } else if ((in.length % 3) == 2) {
            inStream.write(0);
        }
        inStream.writeTo(MimeUtility.encode(out, "base64"));
        return out;
    }

    /**
     * Encode String to base64
     * 
     * @param plaintext
     *            the plaintext to decode
     * @return base64String the encoded String
     * @throws IOException
     *             get thrown when I/O error was detected
     * @throws MessagingException
     *             get thrown when an error was detected while trying to encode
     *             the String
     */
    public static String encodeAsString(String plaintext) throws IOException, MessagingException {
        return encode(plaintext).toString();
    }
}
