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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.MailHook;
import org.apache.james.protocols.smtp.hook.MailParametersHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/**
 * Handles MAIL command
 */
public class MailCmdHandler extends AbstractHookableCmdHandler<MailHook> {
    private static final Collection<String> COMMANDS = ImmutableSet.of("MAIL");
    private static final Logger LOGGER = LoggerFactory.getLogger(MailCmdHandler.class);
    private static final Response SENDER_ALREADY_SPECIFIED =  new SMTPResponse(SMTPRetCode.BAD_SEQUENCE, DSNStatus
            .getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_OTHER)
            + " Sender already specified").immutable();
    private static final Response EHLO_HELO_NEEDED =  new SMTPResponse(SMTPRetCode.BAD_SEQUENCE, DSNStatus
            .getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_OTHER)
            + " Need HELO or EHLO before MAIL").immutable();
    private static final Response SYNTAX_ERROR_ARG = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,
            DSNStatus.getStatus(DSNStatus.PERMANENT,
                    DSNStatus.DELIVERY_INVALID_ARG)
                    + " Usage: MAIL FROM:<sender>").immutable();
    private static final Response SYNTAX_ERROR = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,
            DSNStatus.getStatus(DSNStatus.PERMANENT,
                    DSNStatus.ADDRESS_SYNTAX_SENDER)
                    + " Syntax error in MAIL command").immutable();
    private static final Response SYNTAX_ERROR_ADDRESS = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,
            DSNStatus.getStatus(DSNStatus.PERMANENT,
                    DSNStatus.ADDRESS_SYNTAX_SENDER)
                    + " Syntax error in sender address").immutable();
    /**
     * A map of parameterHooks
     */
    private Map<String, MailParametersHook> paramHooks;

    @Inject
    public MailCmdHandler(MetricFactory metricFactory) {
        super(metricFactory);
    }

    @Override
    public Response onCommand(SMTPSession session, Request request) {
        Response response = super.onCommand(session, request);
        // Check if the response was not ok
        if (response.getRetCode().equals(SMTPRetCode.MAIL_OK) == false) {
            // cleanup the session
            session.removeAttachment(SMTPSession.SENDER, State.Transaction);
        }

        return response;
    }

    /**
     * Handler method called upon receipt of a MAIL command. Sets up handler to
     * deliver mail as the stated sender.
     * 
     * @param session
     *            SMTP session object
     */
    private Response doMAIL(SMTPSession session) {
        StringBuilder responseBuffer = new StringBuilder();
        MaybeSender sender = session.getAttachment(SMTPSession.SENDER, State.Transaction).orElse(MaybeSender.nullSender());
        responseBuffer.append(
                DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.ADDRESS_OTHER))
                .append(" Sender <");
        if (!sender.isNullSender()) {
            responseBuffer.append(sender.asString());
        }
        responseBuffer.append("> OK");
        return new SMTPResponse(SMTPRetCode.MAIL_OK, responseBuffer);
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    protected Response doCoreCmd(SMTPSession session, String command,
                                 String parameters) {
        return doMAIL(session);
    }

    @Override
    protected Response doFilterChecks(SMTPSession session, String command,
                                      String parameters) {
        return doMAILFilter(session, parameters);
    }

    /**
     * @param session
     *            SMTP session object
     * @param argument
     *            the argument passed in with the command by the SMTP client
     */
    private Response doMAILFilter(SMTPSession session, String argument) {
        String sender = null;

        if ((argument != null) && (argument.indexOf(":") > 0)) {
            int colonIndex = argument.indexOf(":");
            sender = argument.substring(colonIndex + 1);
            argument = argument.substring(0, colonIndex);
        }
        if (session.getAttachment(SMTPSession.SENDER, State.Transaction).isPresent()) {
            return SENDER_ALREADY_SPECIFIED;
        } else if (!session.getAttachment(
                SMTPSession.CURRENT_HELO_MODE, State.Connection).isPresent()
                && session.getConfiguration().useHeloEhloEnforcement()) {
            return EHLO_HELO_NEEDED;
        } else if (argument == null
                || !argument.toUpperCase(Locale.US).equals("FROM")
                || sender == null) {
            return SYNTAX_ERROR_ARG;
        } else {
            sender = sender.trim();
            // the next gt after the first lt ... AUTH may add more <>
            int lastChar = sender.indexOf('>', sender.indexOf('<'));
            // Check to see if any options are present and, if so, whether they
            // are correctly formatted
            // (separated from the closing angle bracket by a ' ').
            if ((lastChar > 0) && (sender.length() > lastChar + 2)
                    && (sender.charAt(lastChar + 1) == ' ')) {
                String mailOptionString = sender.substring(lastChar + 2);

                // Remove the options from the sender
                sender = sender.substring(0, lastChar + 1);

                StringTokenizer optionTokenizer = new StringTokenizer(
                        mailOptionString, " ");
                while (optionTokenizer.hasMoreElements()) {
                    String mailOption = optionTokenizer.nextToken();
                    int equalIndex = mailOption.indexOf('=');
                    String mailOptionName = mailOption;
                    String mailOptionValue = "";
                    if (equalIndex > 0) {
                        mailOptionName = mailOption.substring(0, equalIndex)
                                .toUpperCase(Locale.US);
                        mailOptionValue = mailOption.substring(equalIndex + 1);
                    }

                    // Handle the SIZE extension keyword

                    if (paramHooks.containsKey(mailOptionName)) {
                        MailParametersHook hook = paramHooks.get(mailOptionName);
                        SMTPResponse res = calcDefaultSMTPResponse(hook.doMailParameter(session, mailOptionName, mailOptionValue));
                        if (res != null) {
                            return res;
                        }
                    } else {
                        // Unexpected option attached to the Mail command
                        LOGGER.debug("MAIL command had unrecognized/unexpected option {} with value {}", mailOptionName, mailOptionValue);
                    }
                }
            }
            if (session.getConfiguration().useAddressBracketsEnforcement()
                    && (!sender.startsWith("<") || !sender.endsWith(">"))) {
                LOGGER.info("Error parsing sender address: {}: did not start and end with < >", sender);
                return SYNTAX_ERROR;
            }
            try {
                MaybeSender senderAddress = toMaybeSender(removeBrackets(sender));
                // Store the senderAddress in session map
                session.setAttachment(SMTPSession.SENDER, senderAddress, State.Transaction);
            } catch (Exception pe) {
                LOGGER.info("Error parsing sender address: {}", sender, pe);
                return SYNTAX_ERROR_ADDRESS;
            }
        }
        return null;
    }

    private MaybeSender toMaybeSender(String senderAsString) throws AddressException {
        if (senderAsString.length() == 0) {
            // This is the <> case.
            return MaybeSender.nullSender();
        }
        if (senderAsString.equals("@")) {
            return MaybeSender.nullSender();
        }
        return MaybeSender.of(new MailAddress(
            appendDefaultDomainIfNeeded(senderAsString)));
    }

    private String removeBrackets(String input) {
        if (input.startsWith("<") && input.endsWith(">")) {
            // Remove < and >
            return input.substring(1, input.length() - 1);
        }
        return input;
    }

    private String appendDefaultDomainIfNeeded(String address) {
        if (!address.contains("@")) {
            return address + "@" + getDefaultDomain();
        }
        return address;
    }

    @Override
    protected Class<MailHook> getHookInterface() {
        return MailHook.class;
    }

    @Override
    protected HookResult callHook(MailHook rawHook, SMTPSession session, String parameters) {
        MaybeSender sender = session.getAttachment(SMTPSession.SENDER, State.Transaction).orElse(MaybeSender.nullSender());
        return rawHook.doMail(session, sender);
    }
    
    @Override
    public List<Class<?>> getMarkerInterfaces() {
        List<Class<?>> l = super.getMarkerInterfaces();
        l.add(MailParametersHook.class);
        return l;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void wireExtensions(Class interfaceName, List extension) {
        if (MailParametersHook.class.equals(interfaceName)) {
            this.paramHooks = new HashMap<>();
            for (MailParametersHook hook : (Iterable<MailParametersHook>) extension) {
                String[] params = hook.getMailParamNames();
                for (String param : params) {
                    paramHooks.put(param, hook);
                }
            }
        } else {
            super.wireExtensions(interfaceName, extension);
        }
    }

    /**
     * Return the default domain to append if the sender contains none
     * 
     * @return defaultDomain
     */
    protected String getDefaultDomain() {
        return "localhost";
    }
    

}
