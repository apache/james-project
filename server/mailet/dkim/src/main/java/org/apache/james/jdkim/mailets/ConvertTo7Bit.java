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

package org.apache.james.jdkim.mailets;

import java.io.IOException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.base.Converter7Bit;
import org.apache.mailet.base.GenericMailet;

/**
 * Make sure the message stream is 7bit. Every 8bit part is encoded to
 * quoted-printable or base64 and the message is saved.
 */
public class ConvertTo7Bit extends GenericMailet {

    private Converter7Bit converter7Bit;

    @Override
    public void init(MailetConfig newConfig) throws MessagingException {
        super.init(newConfig);
        this.converter7Bit = new Converter7Bit(getMailetContext());
    }

    public void service(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();
        try {
            converter7Bit.convertTo7Bit(message);
        } catch (IOException e) {
            throw new MessagingException("IOException converting message to 7bit: " + e.getMessage(), e);
        }
    }
}
