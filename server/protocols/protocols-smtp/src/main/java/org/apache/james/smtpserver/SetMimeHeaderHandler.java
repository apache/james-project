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

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds the header to the message
 */
public class SetMimeHeaderHandler implements JamesMessageHook, ProtocolHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SetMimeHeaderHandler.class);

    /**
     * The header name and value that needs to be added
     */
    private String headerName;
    private String headerValue;

    /**
     * Set the header name
     * 
     * @param headerName
     *            String which represent the header name
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    /**
     * Set the header value
     * 
     * @param headerValue
     *            String which represents the header value
     */
    public void setHeaderValue(String headerValue) {
        this.headerValue = headerValue;
    }

    /**
     * Adds header to the message
     */
    @Override
    public HookResult onMessage(SMTPSession session, Mail mail) {
        try {
            MimeMessage message = mail.getMessage();

            // Set the header name and value (supplied at init time).
            if (headerName != null) {
                message.setHeader(headerName, headerValue);
                message.saveChanges();
            }

        } catch (MessagingException me) {
            LOGGER.error(me.getMessage());
        }

        return HookResult.DECLINED;
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        setHeaderName(config.getString("headername"));
        setHeaderValue(config.getString("headervalue"));        
    }
}
