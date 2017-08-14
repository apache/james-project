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

package org.apache.james.protocols.smtp.core.esmtp;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.DataLineFilter;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MailParametersHook;
import org.apache.james.protocols.smtp.hook.MessageHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle the ESMTP SIZE extension.
 */
public class MailSizeEsmtpExtension implements MailParametersHook, EhloExtension, DataLineFilter, MessageHook {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailSizeEsmtpExtension.class);

    private final static String MESG_SIZE = "MESG_SIZE"; // The size of the
    private final static String MESG_FAILED = "MESG_FAILED";   // Message failed flag
    private final static String[] MAIL_PARAMS = { "SIZE" };
    
    private static final HookResult SYNTAX_ERROR = new HookResult(HookReturnCode.DENY, SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " Syntactically incorrect value for SIZE parameter");
    private static final HookResult QUOTA_EXCEEDED = new HookResult(HookReturnCode.DENY, SMTPRetCode.QUOTA_EXCEEDED, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SYSTEM_MSG_TOO_BIG) + " Message size exceeds fixed maximum message size");
    public static final int SINGLE_CHARACTER_LINE = 3;
    public static final int DOT_BYTE = 46;

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }

    /**
     * @see org.apache.james.protocols.smtp.hook.MailParametersHook#doMailParameter(org.apache.james.protocols.smtp.SMTPSession, java.lang.String, java.lang.String)
     */
    public HookResult doMailParameter(SMTPSession session, String paramName,
            String paramValue) {
        return doMailSize(session, paramValue,
                (String) session.getAttachment(SMTPSession.SENDER, State.Transaction));
    }

    /**
     * @see org.apache.james.protocols.smtp.hook.MailParametersHook#getMailParamNames()
     */
    public String[] getMailParamNames() {
        return MAIL_PARAMS;
    }

    /**
     * @see org.apache.james.protocols.smtp.core.esmtp.EhloExtension#getImplementedEsmtpFeatures(org.apache.james.protocols.smtp.SMTPSession)
     */
    @SuppressWarnings("unchecked")
    public List<String> getImplementedEsmtpFeatures(SMTPSession session) {
        // Extension defined in RFC 1870
        long maxMessageSize = session.getConfiguration().getMaxMessageSize();
        if (maxMessageSize > 0) {
            return Arrays.asList("SIZE " + maxMessageSize);
        } else {
            return Collections.EMPTY_LIST;
        }
    }


    /**
     * Handles the SIZE MAIL option.
     * 
     * @param session
     *            SMTP session object
     * @param mailOptionValue
     *            the option string passed in with the SIZE option
     * @param tempSender
     *            the sender specified in this mail command (for logging
     *            purpose)
     * @return true if further options should be processed, false otherwise
     */
    private HookResult doMailSize(SMTPSession session,
            String mailOptionValue, String tempSender) {
        int size = 0;
        try {
            size = Integer.parseInt(mailOptionValue);
        } catch (NumberFormatException pe) {
            LOGGER.error("Rejected syntactically incorrect value for SIZE parameter.");
            
            // This is a malformed option value. We return an error
            return SYNTAX_ERROR;
        }
        if (LOGGER.isDebugEnabled()) {
            StringBuilder debugBuffer = new StringBuilder(128).append(
                    "MAIL command option SIZE received with value ").append(
                    size).append(".");
            LOGGER.debug(debugBuffer.toString());
        }
        long maxMessageSize = session.getConfiguration().getMaxMessageSize();
        if ((maxMessageSize > 0) && (size > maxMessageSize)) {
            // Let the client know that the size limit has been hit.
            StringBuilder errorBuffer = new StringBuilder(256).append(
                    "Rejected message from ").append(
                    tempSender != null ? tempSender : null).append(
                    " from ")
                    .append(session.getRemoteAddress().getAddress().getHostAddress()).append(" of size ")
                    .append(size).append(
                            " exceeding system maximum message size of ")
                    .append(maxMessageSize).append("based on SIZE option.");
            LOGGER.error(errorBuffer.toString());

            return QUOTA_EXCEEDED;
        } else {
            // put the message size in the message state so it can be used
            // later to restrict messages for user quotas, etc.
            session.setAttachment(MESG_SIZE, Integer.valueOf(size), State.Transaction);
        }
        return null;
    }


    /**
     * @see org.apache.james.protocols.smtp.core.DataLineFilter#onLine(SMTPSession, byte[], LineHandler)
     */
    public Response onLine(SMTPSession session, ByteBuffer line, LineHandler<SMTPSession> next) {
        Boolean failed = (Boolean) session.getAttachment(MESG_FAILED, State.Transaction);
        // If we already defined we failed and sent a reply we should simply
        // wait for a CRLF.CRLF to be sent by the client.
        if (failed != null && failed) {
            if (isDataTerminated(line)) {
                line.rewind();
                next.onLine(session, line);
                return new SMTPResponse(SMTPRetCode.QUOTA_EXCEEDED, "Quota exceeded");
            } else {
                return null;
            }
        } else {
            if (isDataTerminated(line)) {
                line.rewind();
                return next.onLine(session, line);
            } else {
                line.rewind();
                Long currentSize = (Long) session.getAttachment("CURRENT_SIZE", State.Transaction);
                Long newSize;
                if (currentSize == null) {
                    newSize = Long.valueOf(line.remaining());
                } else {
                    newSize = Long.valueOf(currentSize.intValue()+line.remaining());
                }

                session.setAttachment("CURRENT_SIZE", newSize, State.Transaction);

                if (session.getConfiguration().getMaxMessageSize() > 0 && newSize.intValue() > session.getConfiguration().getMaxMessageSize()) {
                    // Add an item to the state to suppress
                    // logging of extra lines of data
                    // that are sent after the size limit has
                    // been hit.
                    session.setAttachment(MESG_FAILED, Boolean.TRUE, State.Transaction);

                    return null;
                } else {
                    line.rewind();
                    return next.onLine(session, line);
                }
            }
        }
    }

    private boolean isDataTerminated(ByteBuffer line) {
        return line.remaining() == SINGLE_CHARACTER_LINE && line.get() == DOT_BYTE;
    }

    /**
     * @see org.apache.james.protocols.smtp.hook.MessageHook#onMessage(SMTPSession, MailEnvelope)
     */
    public HookResult onMessage(SMTPSession session, MailEnvelope mail) {
        Boolean failed = (Boolean) session.getAttachment(MESG_FAILED, State.Transaction);
        if (failed != null && failed.booleanValue()) {
            
            StringBuilder errorBuffer = new StringBuilder(256).append(
                    "Rejected message from ").append(
                    session.getAttachment(SMTPSession.SENDER, State.Transaction).toString())
                    .append(" from ").append(session.getRemoteAddress().getAddress().getHostAddress())
                    .append(" exceeding system maximum message size of ")
                    .append(
                            session.getConfiguration().getMaxMessageSize());
            LOGGER.error(errorBuffer.toString());
            return QUOTA_EXCEEDED;
        } else {
            return HookResult.declined();
        }
    }

}
