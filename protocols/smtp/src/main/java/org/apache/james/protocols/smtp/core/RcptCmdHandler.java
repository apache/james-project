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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.StringTokenizer;

import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.RcptHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/**
 * Handles RCPT command
 */
public class RcptCmdHandler extends AbstractHookableCmdHandler<RcptHook> implements
        CommandHandler<SMTPSession> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RcptCmdHandler.class);
    public static final String CURRENT_RECIPIENT = "CURRENT_RECIPIENT"; // Current
                                                                        // recipient
    private static final Collection<String> COMMANDS = ImmutableSet.of("RCPT");
    private static final Response MAIL_NEEDED = new SMTPResponse(SMTPRetCode.BAD_SEQUENCE, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_OTHER) + " Need MAIL before RCPT").immutable();
    private static final Response SYNTAX_ERROR_ARGS = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_SYNTAX) + " Usage: RCPT TO:<recipient>").immutable();
    private static final Response SYNTAX_ERROR_DELIVERY = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_SYNTAX) + " Syntax error in parameters or arguments").immutable();
    private static final Response SYNTAX_ERROR_ADDRESS = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_MAILBOX, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.ADDRESS_SYNTAX) + " Syntax error in recipient address").immutable();

    @Inject
    public RcptCmdHandler(MetricFactory metricFactory) {
        super(metricFactory);
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }

    /**
     * Handler method called upon receipt of a RCPT command. Reads recipient.
     * Does some connection validation.
     * 
     * 
     * @param session
     *            SMTP session object
     * @param command
     *            command passed
     * @param parameters
     *            parameters passed in with the command by the SMTP client
     */
    @SuppressWarnings("unchecked")
    protected Response doCoreCmd(SMTPSession session, String command,
            String parameters) {
        Collection<MailAddress> rcptColl = (Collection<MailAddress>) session.getAttachment(
                SMTPSession.RCPT_LIST, State.Transaction);
        if (rcptColl == null) {
            rcptColl = new ArrayList<>();
        }
        MailAddress recipientAddress = (MailAddress) session.getAttachment(
                CURRENT_RECIPIENT, State.Transaction);
        rcptColl.add(recipientAddress);
        session.setAttachment(SMTPSession.RCPT_LIST, rcptColl, State.Transaction);
        StringBuilder response = new StringBuilder();
        response
                .append(
                        DSNStatus.getStatus(DSNStatus.SUCCESS,
                                DSNStatus.ADDRESS_VALID))
                .append(" Recipient <").append(recipientAddress).append("> OK");
        return new SMTPResponse(SMTPRetCode.MAIL_OK, response);

    }

    /**
     * @param session
     *            SMTP session object
     * @param argument
     *            the argument passed in with the command by the SMTP client
     */
    protected Response doFilterChecks(SMTPSession session, String command,
            String argument) {
        String recipient = null;
        if ((argument != null) && (argument.indexOf(":") > 0)) {
            int colonIndex = argument.indexOf(":");
            recipient = argument.substring(colonIndex + 1);
            argument = argument.substring(0, colonIndex);
        }
        if (session.getAttachment(SMTPSession.SENDER, State.Transaction) == null) {
            return MAIL_NEEDED;
        } else if (argument == null
                || !argument.toUpperCase(Locale.US).equals("TO")
                || recipient == null) {
            return SYNTAX_ERROR_ARGS;
        }

        recipient = recipient.trim();
        int lastChar = recipient.lastIndexOf('>');
        // Check to see if any options are present and, if so, whether they
        // are correctly formatted
        // (separated from the closing angle bracket by a ' ').
        String rcptOptionString = null;
        if ((lastChar > 0) && (recipient.length() > lastChar + 2)
                && (recipient.charAt(lastChar + 1) == ' ')) {
            rcptOptionString = recipient.substring(lastChar + 2);

            // Remove the options from the recipient
            recipient = recipient.substring(0, lastChar + 1);
        }
        if (session.getConfiguration().useAddressBracketsEnforcement()
                && (!recipient.startsWith("<") || !recipient.endsWith(">"))) {
            if (LOGGER.isInfoEnabled()) {
                StringBuilder errorBuffer = new StringBuilder(192).append(
                        "Error parsing recipient address: ").append(
                        "Address did not start and end with < >").append(
                        getContext(session, null, recipient));
                LOGGER.info(errorBuffer.toString());
            }
            return SYNTAX_ERROR_DELIVERY;
        }
        MailAddress recipientAddress = null;
        // Remove < and >
        if (session.getConfiguration().useAddressBracketsEnforcement()
                || (recipient.startsWith("<") && recipient.endsWith(">"))) {
            recipient = recipient.substring(1, recipient.length() - 1);
        }

        if (!recipient.contains("@")) {
            // set the default domain
            recipient = recipient
                    + "@"
                    + getDefaultDomain();
        }

        try {
            recipientAddress = new MailAddress(recipient);
        } catch (Exception pe) {
            if (LOGGER.isInfoEnabled()) {
                StringBuilder errorBuffer = new StringBuilder(192).append(
                        "Error parsing recipient address: ").append(
                        getContext(session, recipientAddress, recipient))
                        .append(pe.getMessage());
                LOGGER.info(errorBuffer.toString());
            }
            /*
             * from RFC2822; 553 Requested action not taken: mailbox name
             * not allowed (e.g., mailbox syntax incorrect)
             */
            return SYNTAX_ERROR_ADDRESS;
        }

        if (rcptOptionString != null) {

            StringTokenizer optionTokenizer = new StringTokenizer(
                    rcptOptionString, " ");
            while (optionTokenizer.hasMoreElements()) {
                String rcptOption = optionTokenizer.nextToken();
                int equalIndex = rcptOption.indexOf('=');
                String rcptOptionName = rcptOption;
                String rcptOptionValue = "";
                if (equalIndex > 0) {
                    rcptOptionName = rcptOption.substring(0, equalIndex)
                            .toUpperCase(Locale.US);
                    rcptOptionValue = rcptOption.substring(equalIndex + 1);
                }
                // Unexpected option attached to the RCPT command
                if (LOGGER.isDebugEnabled()) {
                    StringBuilder debugBuffer = new StringBuilder(128)
                            .append(
                                    "RCPT command had unrecognized/unexpected option ")
                            .append(rcptOptionName).append(" with value ")
                            .append(rcptOptionValue).append(
                                    getContext(session, recipientAddress,
                                            recipient));
                    LOGGER.debug(debugBuffer.toString());
                }

                return new SMTPResponse(
                        SMTPRetCode.PARAMETER_NOT_IMPLEMENTED,
                        "Unrecognized or unsupported option: "
                                + rcptOptionName);
            }
            optionTokenizer = null;
        }

        session.setAttachment(CURRENT_RECIPIENT,recipientAddress, State.Transaction);

        return null;
    }

    private String getContext(SMTPSession session, MailAddress recipientAddress, String recipient) {
        StringBuilder sb = new StringBuilder(128);
        if (null != recipientAddress) {
            sb.append(" [to:" + recipientAddress.toString() + "]");
        } else if (null != recipient) {
            sb.append(" [to:" + recipient + "]");
        }
        if (null != session.getAttachment(SMTPSession.SENDER, State.Transaction)) {
            sb.append(" [from:" + ((MailAddress) session.getAttachment(SMTPSession.SENDER, State.Transaction)).toString() + "]");
        }
        return sb.toString();
    }

    /**
     * @see org.apache.james.protocols.api.handler.CommandHandler#getImplCommands()
     */
    public Collection<String> getImplCommands() {
    	return COMMANDS;
    }

    /**
     * @see org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler#getHookInterface()
     */
    protected Class<RcptHook> getHookInterface() {
        return RcptHook.class;
    }

    /**
     * {@inheritDoc}
     */
    protected HookResult callHook(RcptHook rawHook, SMTPSession session,
            String parameters) {
        return rawHook.doRcpt(session,
                (MailAddress) session.getAttachment(SMTPSession.SENDER, State.Transaction),
                (MailAddress) session.getAttachment(CURRENT_RECIPIENT, State.Transaction));
    }

    protected String getDefaultDomain() {
    	return "localhost";
    }
}
