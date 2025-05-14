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

import java.util.Enumeration;

import jakarta.mail.Header;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnforceHeaderLimitationsMessageHook implements JamesMessageHook {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnforceHeaderLimitationsMessageHook.class);
    private static final int DEFAULT_MAX_LINES = 500;
    private static final int DEFAULT_MAX_SIZE = 1024 * 64;

    private int maxLines;
    private int maxSize;

    @Override
    public HookResult onMessage(SMTPSession session, Mail mail) {
        try {
            int actualLines = 0;
            int actualSize = 0;
            Enumeration<Header> headers = mail.getMessage().getAllHeaders();
            while (headers.hasMoreElements()) {
               Header header = headers.nextElement();
               actualLines += 1;
               actualSize += header.getName().length() + header.getValue().length() + 4;
               if (actualLines > maxLines) {
                   LOGGER.warn("Email rejected: too many header lines");
                   return HookResult.builder()
                       .hookReturnCode(HookReturnCode.denySoft())
                       .smtpReturnCode(SMTPRetCode.QUOTA_EXCEEDED)
                       .smtpDescription("Header Lines are too many")
                       .build();
               }
               if (actualSize > maxSize) {
                    LOGGER.warn("Email rejected: header size too large");
                    return HookResult.builder()
                        .hookReturnCode(HookReturnCode.denySoft())
                        .smtpReturnCode(SMTPRetCode.QUOTA_EXCEEDED)
                        .smtpDescription("Header size is too large")
                        .build();
               }
           }
           return HookResult.DECLINED;
        } catch (Exception e) {
            LOGGER.warn("Error while checking header size", e);
            return HookResult.DENY;
        }
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        this.maxLines = config.getInt("maxLines") <= 0 ? DEFAULT_MAX_LINES : config.getInt("maxLines", DEFAULT_MAX_LINES);
        this.maxSize = config.getInt("maxSize") <= 0 ? DEFAULT_MAX_SIZE : config.getInt("maxSize", DEFAULT_MAX_SIZE) * 1024;
    }
}

