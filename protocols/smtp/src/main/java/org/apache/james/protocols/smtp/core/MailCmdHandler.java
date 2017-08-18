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

import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.MailHook;
import org.apache.james.protocols.smtp.hook.MailParametersHook;

import com.google.common.collect.ImmutableSet;

/**
 * Handles MAIL command
 */
public class MailCmdHandler extends AbstractHookableCmdHandler<MailHook> {
    private static final Collection<String> COMMANDS = ImmutableSet.of("MAIL");
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
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }

    /**
     * @see
     * org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler
     * #onCommand(SMTPSession, Request)
     */
    public Response onCommand(SMTPSession session, Request request) {
        Response response = super.onCommand(session, request);
        // Check if the response was not ok
        if (response.getRetCode().equals(SMTPRetCode.MAIL_OK) == false) {
            // cleanup the session
            session.setAttachment(SMTPSession.SENDER, null,  State.Transaction);
        }

        return response;
    }

	/**
     * Handler method called upon receipt of a MAIL command. Sets up handler to
     * deliver mail as the stated sender.
     * 
     * @param session
     *            SMTP session object
     * @param argument
     *            the argument passed in with the command by the SMTP client
     */
    private Response doMAIL(SMTPSession session, String argument) {
        StringBuilder responseBuffer = new StringBuilder();
        MailAddress sender = (MailAddress) session.getAttachment(
                SMTPSession.SENDER, State.Transaction);
        responseBuffer.append(
                DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.ADDRESS_OTHER))
                .append(" Sender <");
        if (sender != null) {
            responseBuffer.append(sender);
        }
        responseBuffer.append("> OK");
        return new SMTPResponse(SMTPRetCode.MAIL_OK, responseBuffer);
    }

    /**
     * @see org.apache.james.protocols.api.handler.CommandHandler#getImplCommands()
     */
    public Collection<String> getImplCommands() {
    	return COMMANDS;
    }

    /**
     * @see org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler#doCoreCmd(org.apache.james.protocols.smtp.SMTPSession,
     *      java.lang.String, java.lang.String)
     */
    protected Response doCoreCmd(SMTPSession session, String command,
            String parameters) {
        return doMAIL(session, parameters);
    }

    /**
     * @see org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler#doFilterChecks(org.apache.james.protocols.smtp.SMTPSession,
     *      java.lang.String, java.lang.String)
     */
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
        if (session.getAttachment(SMTPSession.SENDER, State.Transaction) != null) {
            return SENDER_ALREADY_SPECIFIED;
        } else if (session.getAttachment(
                SMTPSession.CURRENT_HELO_MODE, State.Connection) == null
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
                        if (session.getLogger().isDebugEnabled()) {
                            StringBuilder debugBuffer = new StringBuilder(128)
                                    .append(
                                            "MAIL command had unrecognized/unexpected option ")
                                    .append(mailOptionName).append(
                                            " with value ").append(
                                            mailOptionValue);
                            session.getLogger().debug(debugBuffer.toString());
                        }
                    }
                }
            }
            if (session.getConfiguration().useAddressBracketsEnforcement()
                    && (!sender.startsWith("<") || !sender.endsWith(">"))) {
                if (session.getLogger().isInfoEnabled()) {
                    StringBuilder errorBuffer = new StringBuilder(128).append(
                            "Error parsing sender address: ").append(sender)
                            .append(": did not start and end with < >");
                    session.getLogger().info(errorBuffer.toString());
                }
                return SYNTAX_ERROR;
            }
            MailAddress senderAddress = null;

            if (session.getConfiguration().useAddressBracketsEnforcement()
                    || (sender.startsWith("<") && sender.endsWith(">"))) {
                // Remove < and >
                sender = sender.substring(1, sender.length() - 1);
            }

            if (sender.length() == 0) {
                // This is the <> case. Let senderAddress == null
            } else {

                if (!sender.contains("@")) {
                    sender = sender
                            + "@"
                            + getDefaultDomain();
                }

                try {
                    senderAddress = new MailAddress(sender);
                } catch (Exception pe) {
                    if (session.getLogger().isInfoEnabled()) {
                        StringBuilder errorBuffer = new StringBuilder(256)
                                .append("Error parsing sender address: ")
                                .append(sender).append(": ").append(
                                        pe.getMessage());
                        session.getLogger().info(errorBuffer.toString());
                    }
                    return SYNTAX_ERROR_ADDRESS;
                }
            }
            if ((senderAddress == null) || 
                    ((senderAddress.getLocalPart().length() == 0) && (senderAddress.getDomain().length() == 0))) {
                senderAddress = MailAddress.nullSender();
            }
            // Store the senderAddress in session map
            session.setAttachment(SMTPSession.SENDER, senderAddress, State.Transaction);
        }
        return null;
    }
    /**
     * @see org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler#getHookInterface()
     */
    protected Class<MailHook> getHookInterface() {
        return MailHook.class;
    }


    /**
     * {@inheritDoc}
     */
    protected HookResult callHook(MailHook rawHook, SMTPSession session, String parameters) {
        MailAddress sender = (MailAddress) session.getAttachment(SMTPSession.SENDER, State.Transaction);
        if (sender.isNullSender()) {
            sender = null;
        }
        return rawHook.doMail(session, sender);
    }

    
    /**
     * @see org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler#getMarkerInterfaces()
     */
    public List<Class<?>> getMarkerInterfaces() {
        List<Class<?>> l = super.getMarkerInterfaces();
        l.add(MailParametersHook.class);
        return l;
    }

    /**
     * @see org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler#wireExtensions(java.lang.Class, java.util.List)
     */
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
