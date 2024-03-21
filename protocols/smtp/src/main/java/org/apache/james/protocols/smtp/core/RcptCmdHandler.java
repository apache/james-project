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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.RcptHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Handles RCPT command
 */
public class RcptCmdHandler extends AbstractHookableCmdHandler<RcptHook> implements
        CommandHandler<SMTPSession> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RcptCmdHandler.class);
    public static final ProtocolSession.AttachmentKey<MailAddress> CURRENT_RECIPIENT = ProtocolSession.AttachmentKey.of("CURRENT_RECIPIENT", MailAddress.class);
    public static final ProtocolSession.AttachmentKey<Map> CURRENT_RECIPIENT_PARAMETERS = ProtocolSession.AttachmentKey.of("CURRENT_RECIPIENT_PARAMETERS", Map.class);
    private static final Collection<String> COMMANDS = ImmutableSet.of("RCPT");
    private static final Response MAIL_NEEDED = new SMTPResponse(SMTPRetCode.BAD_SEQUENCE, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_OTHER) + " Need MAIL before RCPT").immutable();
    private static final Response SYNTAX_ERROR_ARGS = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_SYNTAX) + " Usage: RCPT TO:<recipient>").immutable();
    private static final Response SYNTAX_ERROR_DELIVERY = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_SYNTAX) + " Syntax error in parameters or arguments").immutable();
    private static final Response SYNTAX_ERROR_ADDRESS = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_MAILBOX, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.ADDRESS_SYNTAX) + " Syntax error in recipient address").immutable();

    @Inject
    public RcptCmdHandler(MetricFactory metricFactory) {
        super(metricFactory);
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
    @Override
    protected Response doCoreCmd(SMTPSession session, String command, String parameters) {
        List<MailAddress> rcptColl = session.getAttachment(SMTPSession.RCPT_LIST, State.Transaction).orElseGet(ArrayList::new);
        MailAddress recipientAddress = session.getAttachment(CURRENT_RECIPIENT, State.Transaction).orElse(MailAddress.nullSender());
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
    @Override
    protected Response doFilterChecks(SMTPSession session, String command,
                                      String argument) {
        String recipient = null;
        if ((argument != null) && (argument.indexOf(":") > 0)) {
            int colonIndex = argument.indexOf(":");
            recipient = argument.substring(colonIndex + 1);
            argument = argument.substring(0, colonIndex);
        }
        if (!session.getAttachment(SMTPSession.SENDER, State.Transaction).isPresent()) {
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
            LOGGER.info("Error parsing recipient address: Address did not start and end with < >{}", getContext(session, null, recipient));
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
            LOGGER.info("Error parsing recipient address{}", getContext(session, recipientAddress, recipient), pe);
            /*
             * from RFC2822; 553 Requested action not taken: mailbox name
             * not allowed (e.g., mailbox syntax incorrect)
             */
            return SYNTAX_ERROR_ADDRESS;
        }


        ImmutableMap.Builder<String, String> parameters = ImmutableMap.builder();
        if (rcptOptionString != null) {

            StringTokenizer optionTokenizer = new StringTokenizer(
                    rcptOptionString, " ");
            while (optionTokenizer.hasMoreElements()) {
                String rcptOption = optionTokenizer.nextToken();
                Pair<String, String> parameter = parseParameter(rcptOption);

                if (!supportedParameter(parameter.getKey())) {
                    // Unexpected option attached to the RCPT command
                    LOGGER.debug("RCPT command had unrecognized/unexpected option {} with value {}{}",
                        parameter.getKey(), parameter.getValue(), getContext(session, recipientAddress, recipient));

                    return new SMTPResponse(
                        SMTPRetCode.PARAMETER_NOT_IMPLEMENTED,
                        "Unrecognized or unsupported option: "
                            + parameter.getKey());
                }
                parameters.put(parameter.getKey(), parameter.getValue());
            }
            optionTokenizer = null;
        }
        session.setAttachment(CURRENT_RECIPIENT_PARAMETERS, parameters.build(), State.Transaction);

        session.setAttachment(CURRENT_RECIPIENT, recipientAddress, State.Transaction);

        return null;
    }

    private String getContext(SMTPSession session, MailAddress recipientAddress, String recipient) {
        StringBuilder sb = new StringBuilder(128);
        if (null != recipientAddress) {
            sb.append(" [to:").append(recipientAddress.asString()).append(']');
        } else if (null != recipient) {
            sb.append(" [to:").append(recipient).append(']');
        }

        MaybeSender sender = session.getAttachment(SMTPSession.SENDER, State.Transaction).orElse(MaybeSender.nullSender());
        if (!sender.isNullSender()) {
            sb.append(" [from:").append(sender.asString()).append(']');
        }
        return sb.toString();
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    protected Class<RcptHook> getHookInterface() {
        return RcptHook.class;
    }

    @Override
    protected HookResult callHook(RcptHook rawHook, SMTPSession session, String parametersString) {
        MaybeSender sender = session.getAttachment(SMTPSession.SENDER, State.Transaction).orElse(MaybeSender.nullSender());
        MailAddress rcpt = session.getAttachment(CURRENT_RECIPIENT, State.Transaction).orElse(MailAddress.nullSender());
        Map<String, String> parameters = session.getAttachment(CURRENT_RECIPIENT_PARAMETERS, State.Transaction).orElseGet(ImmutableMap::of);

        return rawHook.doRcpt(session, sender, rcpt, parameters);
    }

    private Pair<String, String> parseParameter(String rcptOption) {
        int equalIndex = rcptOption.indexOf('=');
        if (equalIndex > 0) {
            return Pair.of(rcptOption.substring(0, equalIndex)
                    .toUpperCase(Locale.US),
                rcptOption.substring(equalIndex + 1));
        } else {
            return Pair.of(rcptOption, "");
        }
    }

    private boolean supportedParameter(String parameterName) {
        return getHooks().stream()
            .anyMatch(rcptHook -> rcptHook.supportedParameters().contains(parameterName));
    }

    protected String getDefaultDomain() {
        return "localhost";
    }
}
