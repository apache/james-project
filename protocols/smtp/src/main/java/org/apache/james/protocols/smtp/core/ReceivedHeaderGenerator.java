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

package org.apache.james.protocols.smtp.core;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPSession;

import com.google.common.collect.ImmutableList;

public class ReceivedHeaderGenerator {
    private static final DateTimeFormatter DATEFORMAT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z (zzz)", Locale.US);

    private static final String EHLO = "EHLO";
    private static final String SMTP = "SMTP";
    private static final String ESMTPA = "ESMTPA";
    private static final String ESMTPSA = "ESMTPSA";
    private static final String ESMTP = "ESMTP";
    private static final String ESMTPS = "ESMTPS";
    private final ProtocolSession.AttachmentKey<Integer> mtPriority = ProtocolSession.AttachmentKey.of("MT-PRIORITY", Integer.class);

    /**
     * Return the service type which will be used in the Received headers.
     */
    protected String getServiceType(SMTPSession session, String heloMode) {
        // Check if EHLO was used
        if (EHLO.equals(heloMode)) {
            // Not successful auth
            if (session.getUsername() == null) {
                if (session.isTLSStarted()) {
                    return ESMTPS;
                } else {
                    return ESMTP;
                }
            } else {
                // See RFC3848:
                // The new keyword "ESMTPA" indicates the use of ESMTP when the SMTP
                // AUTH [3] extension is also used and authentication is successfully achieved.
                if (session.isTLSStarted()) {
                    return ESMTPSA;
                } else {
                    return ESMTPA;
                }
            }
        } else {
            return SMTP;
        }
    }

    public ReceivedDataLineFilter.Header generateReceivedHeader(SMTPSession session) {
        StringBuilder headerLineBuffer = new StringBuilder();

        Optional<String> heloMode = session.getAttachment(SMTPSession.CURRENT_HELO_MODE, ProtocolSession.State.Connection);
        Optional<String> heloName = session.getAttachment(SMTPSession.CURRENT_HELO_NAME, ProtocolSession.State.Connection);

        // Put our Received header first
        headerLineBuffer.append("from ").append(session.getRemoteAddress().getHostName());

        if (heloName.isPresent() && heloMode.isPresent()) {
            headerLineBuffer.append(" (").append(heloMode.get()).append(" ").append(heloName.get()).append(")");
        }
        headerLineBuffer.append(" ([").append(session.getRemoteAddress().getAddress().getHostAddress()).append("])");
        ReceivedDataLineFilter.Header header = new ReceivedDataLineFilter.Header("Received", headerLineBuffer.toString());

        headerLineBuffer = new StringBuilder();

        session.getSSLSession()
            .map(sslSession -> String.format("(using %s with cipher %s)",
                sslSession.getProtocol(),
                Optional.ofNullable(sslSession.getCipherSuite())
                    .orElse("")))
            .ifPresent(header::add);

        headerLineBuffer.append("by ").append(session.getConfiguration().getHelloName()).append(" (").append(session.getConfiguration().getSoftwareName()).append(") with ").append(getServiceType(session, heloMode.orElse("NOT-DEFINED")));
        headerLineBuffer.append(" ID ").append(session.getSessionID());

        List<MailAddress> rcptList = session.getAttachment(SMTPSession.RCPT_LIST, ProtocolSession.State.Transaction).orElse(ImmutableList.of());

        String priorityValue = session.getAttachment(mtPriority, ProtocolSession.State.Transaction)
            .map(p -> " (PRIORITY " + p + ")").orElse("");

        if (rcptList.size() == 1) {
            // Only indicate a recipient if they're the only recipient
            // (prevents email address harvesting and large headers in
            // bulk email)
            header.add(headerLineBuffer.toString());

            headerLineBuffer = new StringBuilder();
            headerLineBuffer.append("for <").append(rcptList.getFirst().toString()).append(">");
            headerLineBuffer.append(priorityValue).append(";");
        } else {
            // Put the ; on the end of the 'by' line
            headerLineBuffer.append(priorityValue).append(";");
        }
        header.add(headerLineBuffer.toString());
        headerLineBuffer = new StringBuilder();

        headerLineBuffer.append(DATEFORMAT.format(ZonedDateTime.now()));

        header.add(headerLineBuffer.toString());
        return header;
    }
}
