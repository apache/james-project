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

import jakarta.inject.Inject;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.DataCmdHandler;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.MimeMessageInputStreamSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * handles DATA command
 */
public class JamesDataCmdHandler extends DataCmdHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(JamesDataCmdHandler.class);

    @Inject
    public JamesDataCmdHandler(MetricFactory metricFactory) {
        super(metricFactory);
    }

    /**
     * Handler method called upon receipt of a DATA command. Reads in message
     * data, creates header, and delivers to mail server service for delivery.
     *
     * @param session  SMTP session object
     * @param argument the argument passed in with the command by the SMTP client
     */
    @Override
    protected SMTPResponse doDATA(SMTPSession session, String argument) {
        try {
            MimeMessageInputStreamSource mmiss = MimeMessageInputStreamSource.create(MailImpl.getId());
            ExtendedSMTPSession extendedSMTPSession = (ExtendedSMTPSession) session;
            extendedSMTPSession.setMimeMessageInputStreamSource(mmiss);
        } catch (Exception e) {
            LOGGER.warn("Error creating mimemessagesource for incoming data", e);
            return new SMTPResponse(SMTPRetCode.LOCAL_ERROR, "Unexpected error preparing to receive DATA.");
        }

        session.pushLineHandler(getLineHandler());

        return new SMTPResponse(SMTPRetCode.DATA_READY, "Ok Send data ending with <CRLF>.<CRLF>");
    }

}
