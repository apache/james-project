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



package org.apache.james.transport.mailets.debug;

import java.io.IOException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Debugging purpose Mailet.  Sends the message to System.err
 *
 */
public class DumpSystemErr extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(DumpSystemErr.class);

    /**
     * Writes the message to System.err .
     *
     * @param mail the mail to process
     *
     * @throws MessagingException if an error occurs while writing the message
     */
    @Override
    public void service(Mail mail) throws MessagingException {
        try {
            MimeMessage message = mail.getMessage();
            message.writeTo(System.err);
        } catch (IOException ioe) {
            LOGGER.error("error printing message", ioe);
        }
    }

    @Override
    public String getMailetInfo() {
        return "Dumps message to System.err";
    }
}
