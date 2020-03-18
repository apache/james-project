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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.smtp.SMTPSession;

import com.google.common.collect.ImmutableList;

/**
 * {@link AbstractAddHeadersFilter} which adds the Received header for the message.
 */
public class ReceivedDataLineFilter extends AbstractAddHeadersFilter {
    private static final String EHLO = "EHLO";
    private static final String SMTP = "SMTP";
    private static final String ESMTPA = "ESMTPA";
    private static final String ESMTP = "ESMTP";
    
    private static final ThreadLocal<DateFormat> DATEFORMAT = ThreadLocal.withInitial(() -> {
        // See RFC822 for the format
        return new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z (zzz)", Locale.US);
    });

    /**
     * Return the service type which will be used in the Received headers.
     */
    protected String getServiceType(SMTPSession session, String heloMode) {
        // Check if EHLO was used
        if (EHLO.equals(heloMode)) {
            // Not successful auth
            if (session.getUsername() == null) {
                return ESMTP;
            } else {
                // See RFC3848
                // The new keyword "ESMTPA" indicates the use of ESMTP when
                // the
                // SMTP
                // AUTH [3] extension is also used and authentication is
                // successfully
                // achieved.
                return ESMTPA;
            }
        } else {
            return SMTP;
        }
    }

    /**
     * The Received header is added in front of the received headers. So returns {@link Location#Suffix}
     */
    @Override
    protected Location getLocation() {
        return Location.Prefix;
    }

    /**
     * Returns the Received header for the message.
     */
    @SuppressWarnings("unchecked")
    @Override
    protected Collection<Header> headers(SMTPSession session) {

        StringBuilder headerLineBuffer = new StringBuilder();

        Optional<String> heloMode = session.getAttachment(SMTPSession.CURRENT_HELO_MODE, State.Connection);
        Optional<String> heloName = session.getAttachment(SMTPSession.CURRENT_HELO_NAME, State.Connection);

        // Put our Received header first
        headerLineBuffer.append("from ").append(session.getRemoteAddress().getHostName());

        if (heloName.isPresent() && heloMode.isPresent()) {
            headerLineBuffer.append(" (").append(heloMode.get()).append(" ").append(heloName.get()).append(")");
        }
        headerLineBuffer.append(" ([").append(session.getRemoteAddress().getAddress().getHostAddress()).append("])");
        Header header = new Header("Received", headerLineBuffer.toString());
        
        headerLineBuffer = new StringBuilder();
        headerLineBuffer.append("by ").append(session.getConfiguration().getHelloName()).append(" (").append(session.getConfiguration().getSoftwareName()).append(") with ").append(getServiceType(session, heloMode.orElse("NOT-DEFINED")));
        headerLineBuffer.append(" ID ").append(session.getSessionID());

        List<MailAddress> rcptList = session.getAttachment(SMTPSession.RCPT_LIST, State.Transaction).orElse(ImmutableList.of());
        if (rcptList.size() == 1) {
            // Only indicate a recipient if they're the only recipient
            // (prevents email address harvesting and large headers in
            // bulk email)
            header.add(headerLineBuffer.toString());
            
            headerLineBuffer = new StringBuilder();
            headerLineBuffer.append("for <").append(rcptList.get(0).toString()).append(">;");
        } else {
            // Put the ; on the end of the 'by' line
            headerLineBuffer.append(";");
        }
        header.add(headerLineBuffer.toString());
        headerLineBuffer = new StringBuilder();

        headerLineBuffer.append(DATEFORMAT.get().format(new Date()));

        header.add(headerLineBuffer.toString());
        
        return Arrays.asList(header);
    
    }
    
}
