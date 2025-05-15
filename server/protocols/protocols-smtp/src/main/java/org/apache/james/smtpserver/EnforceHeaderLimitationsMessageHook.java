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
import org.apache.james.util.Size;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements an SMTP hook to enforce limitations on the headers of incoming emails.
 *
 * It allows configuring and enforcing two types of restrictions:
 * - A maximum number of header lines (default: 500).
 * - A maximum total size of headers in bytes (default: 64 KB).
 * If any of these thresholds are exceeded, the message is rejected with an SMTP error code:
 *   <code>552 Too many header lines</code> if the number of lines exceeds the limit.
 *   <code>552 Header size too large</code> if the total size exceeds the limit.
 *
 * Example XML configuration:
 * <pre>{
 * <handler class="org.apache.james.smtpserver.EnforceHeaderLimitationsMessageHook">
 *     <maxLines>500</maxLines>
 *     <maxSize>64KB</maxSize>
 * </handler>
 * }</pre>
 *
 */

public class EnforceHeaderLimitationsMessageHook implements JamesMessageHook {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnforceHeaderLimitationsMessageHook.class);
    private static final int DEFAULT_MAX_LINES = 500;
    private static final int DEFAULT_MAX_SIZE = 1024 * 64;

    private int maxLines;
    private long maxSize;

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
        long size = Size.parse(config.getString("maxSize")).asBytes();
        this.maxSize = size > 0 ? size : DEFAULT_MAX_SIZE;
    }
}

