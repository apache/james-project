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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.api.ProtocolSession;
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

    private static final ProtocolSession.AttachmentKey<Integer> MESG_SIZE = ProtocolSession.AttachmentKey.of("MESG_SIZE", Integer.class); // The size of the
    private static final ProtocolSession.AttachmentKey<Boolean> MESG_FAILED = ProtocolSession.AttachmentKey.of("MESG_FAILED", Boolean.class);   // Message failed flag
    private static final String[] MAIL_PARAMS = { "SIZE" };
    
    private static final HookResult SYNTAX_ERROR = HookResult.builder()
        .hookReturnCode(HookReturnCode.deny())
        .smtpReturnCode(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS)
        .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " Syntactically incorrect value for SIZE parameter")
        .build();
    private static final HookResult QUOTA_EXCEEDED = HookResult.builder()
        .hookReturnCode(HookReturnCode.deny())
        .smtpReturnCode(SMTPRetCode.QUOTA_EXCEEDED)
        .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SYSTEM_MSG_TOO_BIG) + " Message size exceeds fixed maximum message size")
        .build();
    public static final int SINGLE_CHARACTER_LINE = 3;
    public static final int DOT_BYTE = 46;

    @Override
    public HookResult doMailParameter(SMTPSession session, String paramName,
                                      String paramValue) {
        MaybeSender tempSender = session.getAttachment(SMTPSession.SENDER, State.Transaction).orElse(MaybeSender.nullSender());
        return doMailSize(session, paramValue, tempSender);
    }

    @Override
    public String[] getMailParamNames() {
        return MAIL_PARAMS;
    }

    @Override
    public List<String> getImplementedEsmtpFeatures(SMTPSession session) {
        // Extension defined in RFC 1870
        long maxMessageSize = session.getConfiguration().getMaxMessageSize();
        if (maxMessageSize > 0) {
            return Arrays.asList("SIZE " + maxMessageSize);
        } else {
            return Collections.emptyList();
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
            String mailOptionValue, MaybeSender tempSender) {
        int size = 0;
        try {
            size = Integer.parseInt(mailOptionValue);
        } catch (NumberFormatException pe) {
            LOGGER.error("Rejected syntactically incorrect value for SIZE parameter.");
            
            // This is a malformed option value. We return an error
            return SYNTAX_ERROR;
        }
        LOGGER.debug("MAIL command option SIZE received with value {}.", size);
        long maxMessageSize = session.getConfiguration().getMaxMessageSize();
        if ((maxMessageSize > 0) && (size > maxMessageSize)) {
            // Let the client know that the size limit has been hit.
            LOGGER.info("Rejected message from {} to {} of size {} exceeding system maximum message size of {} based on SIZE option.",
                tempSender.asPrettyString(),
                session.getRemoteAddress().getAddress().getHostAddress(),
                size,
                maxMessageSize);

            return QUOTA_EXCEEDED;
        } else {
            // put the message size in the message state so it can be used
            // later to restrict messages for user quotas, etc.
            session.setAttachment(MESG_SIZE, size, State.Transaction);
        }
        return null;
    }


    @Override
    public Response onLine(SMTPSession session, byte[] line, LineHandler<SMTPSession> next) {
        Optional<Boolean> failed = session.getAttachment(MESG_FAILED, State.Transaction);
        // If we already defined we failed and sent a reply we should simply
        // wait for a CRLF.CRLF to be sent by the client.
        if (failed.isPresent() && failed.get()) {
            if (isDataTerminated(line)) {
                next.onLine(session, line);
                return new SMTPResponse(SMTPRetCode.QUOTA_EXCEEDED, "Quota exceeded");
            } else {
                return null;
            }
        } else {
            if (isDataTerminated(line)) {
                return next.onLine(session, line);
            } else {
                Long newSize = Optional.ofNullable(session.currentMessageSize())
                    .map(currentSize -> Long.valueOf(currentSize.intValue() + line.length))
                    .orElseGet(() -> Long.valueOf(line.length));

                session.setCurrentMessageSize(newSize);

                if (session.getConfiguration().getMaxMessageSize() > 0 && newSize.intValue() > session.getConfiguration().getMaxMessageSize()) {
                    // Add an item to the state to suppress
                    // logging of extra lines of data
                    // that are sent after the size limit has
                    // been hit.
                    session.setAttachment(MESG_FAILED, Boolean.TRUE, State.Transaction);

                    return null;
                } else {
                    return next.onLine(session, line);
                }
            }
        }
    }

    private boolean isDataTerminated(byte[] line) {
        return line.length == SINGLE_CHARACTER_LINE && line[0] == DOT_BYTE;
    }

    @Override
    public HookResult onMessage(SMTPSession session, MailEnvelope mail) {
        Optional<Boolean> failed = session.getAttachment(MESG_FAILED, State.Transaction);
        if (failed.orElse(false)) {
            LOGGER.info("Rejected message from {} from {} exceeding system maximum message size of {}",
                session.getAttachment(SMTPSession.SENDER, State.Transaction).orElse(MaybeSender.nullSender()).asPrettyString(),
                session.getRemoteAddress().getAddress().getHostAddress(), session.getConfiguration().getMaxMessageSize());
            return QUOTA_EXCEEDED;
        } else {
            return HookResult.DECLINED;
        }
    }

}
