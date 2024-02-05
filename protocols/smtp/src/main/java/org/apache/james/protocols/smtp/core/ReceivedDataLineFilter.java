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

import java.io.UnsupportedEncodingException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.core.MailAddress;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.smtp.SMTPSession;

import com.google.common.collect.ImmutableList;

/**
 * {@link SeparatingDataLineFilter} which adds the Received header for the message.
 */
public class ReceivedDataLineFilter extends SeparatingDataLineFilter {

    private static final String EHLO = "EHLO";
    private static final String SMTP = "SMTP";
    private static final String ESMTPA = "ESMTPA";
    private static final String ESMTP = "ESMTP";

    private static final DateTimeFormatter DATEFORMAT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z (zzz)", Locale.US);

    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private final ProtocolSession.AttachmentKey<Boolean> headersPrefixAdded = ProtocolSession.AttachmentKey.of("HEADERS_PREFIX_ADDED" + COUNTER.incrementAndGet(), Boolean.class);
    private final ProtocolSession.AttachmentKey<Boolean> headersSuffixAdded = ProtocolSession.AttachmentKey.of("HEADERS_SUFFIX_ADDED" + COUNTER.incrementAndGet(), Boolean.class);

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
     * The Received header is added in front of the received headers. So returns {@link Location#PREFIX}
     */
    protected Location getLocation() {
        return Location.PREFIX;
    }

    /**
     * Returns the Received header for the message.
     */
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

        session.getSSLSession()
            .map(sslSession -> String.format("(using %s with cipher %s)",
                sslSession.getProtocol(),
                Optional.ofNullable(sslSession.getCipherSuite())
                    .orElse("")))
                .ifPresent(header::add);

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

        headerLineBuffer.append(DATEFORMAT.format(ZonedDateTime.now()));

        header.add(headerLineBuffer.toString());
        
        return Collections.singletonList(header);
    
    }

    @Override
    protected Response onSeparatorLine(SMTPSession session, byte[] line, LineHandler<SMTPSession> next) {
        if (getLocation() == Location.SUFFIX && !session.getAttachment(headersSuffixAdded, State.Transaction).isPresent()) {
            session.setAttachment(headersSuffixAdded, Boolean.TRUE, State.Transaction);
            return addHeaders(session, line, next);
        }
        return super.onSeparatorLine(session, line, next);
    }

    @Override
    protected Response onHeadersLine(SMTPSession session, byte[] line, LineHandler<SMTPSession> next) {
        if (getLocation() == Location.PREFIX && !session.getAttachment(headersPrefixAdded, State.Transaction).isPresent()) {
            session.setAttachment(headersPrefixAdded, Boolean.TRUE, State.Transaction);
            return addHeaders(session, line, next);
        }
        return super.onHeadersLine(session, line, next);
    }

    /**
     * Add headers to the message
     *
     * @return response
     */
    private Response addHeaders(SMTPSession session, byte[] line, LineHandler<SMTPSession> next) {
        Response response;
        for (Header header: headers(session)) {
            response = header.transferTo(session, next);
            if (response != null) {
                return response;
            }
        }
        return next.onLine(session, line);
    }

    enum Location {
        PREFIX,
        SUFFIX
    }

    public static final class Header {
        public static final String MULTI_LINE_PREFIX = "          ";

        public final String name;
        public final List<String> values = new ArrayList<>();

        public Header(String name, String value) {
            this.name = name;
            this.values.add(value);
        }

        /**
         * Add the value to the header
         */
        public Header add(String value) {
            values.add(value);
            return this;
        }


        /**
         * Transfer the content of the {@link Header} to the given {@link LineHandler}.
         *
         * This is done for each line of the {@link Header} until the end is reached or the {@link LineHandler#onLine(org.apache.james.protocols.api.ProtocolSession, byte[])}
         * return <code>non-null</code>
         *
         * @return response
         */
        public Response transferTo(SMTPSession session, LineHandler<SMTPSession> handler) {
            String charset = session.getCharset().name();

            try {
                Response response = null;
                for (int i = 0; i < values.size(); i++) {
                    String line;
                    if (i == 0) {
                        line = name + ": " + values.get(i);
                    } else {
                        line = MULTI_LINE_PREFIX + values.get(i);
                    }
                    response = handler.onLine(session, (line + session.getLineDelimiter()).getBytes(charset));
                    if (response != null) {
                        break;
                    }
                }
                return response;
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("NO " + charset + " support ?", e);
            }
        }
    }
}
