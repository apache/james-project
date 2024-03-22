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

import jakarta.inject.Inject;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HeloHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;

/**
 * Handles HELO command
 */
public class HeloCmdHandler extends AbstractHookableCmdHandler<HeloHook> {

    private static final String COMMAND_NAME = "HELO";
    /**
     * The name of the command handled by the command handler
     */
    private static final Collection<String> COMMANDS = ImmutableSet.of(COMMAND_NAME);

    private static final Response DOMAIN_REQUIRED =  new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,
            DSNStatus.getStatus(DSNStatus.PERMANENT,
                    DSNStatus.DELIVERY_INVALID_ARG)
                    + " Domain address required: " + COMMAND_NAME).immutable();
    public static final Logger LOGGER = LoggerFactory.getLogger(HeloCmdHandler.class);

    @Inject
    public HeloCmdHandler(MetricFactory metricFactory) {
        super(metricFactory);
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    protected Response doCoreCmd(SMTPSession session, String command,
                                 String parameters) {

        if (!isValid(parameters)) {
            LOGGER.error("Invalid EHLO argument received: {}. Must be a domain name or an IP address.", parameters);
            return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,
                DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_SYNTAX) + " Invalid domain name or ip supplied as HELO argument");
        }

        session.setAttachment(SMTPSession.CURRENT_HELO_MODE, COMMAND_NAME, ProtocolSession.State.Connection);
        StringBuilder response = new StringBuilder();
        response.append(session.getConfiguration().getHelloName()).append(
                " Hello ").append(parameters).append(" [").append(
                session.getRemoteAddress().getAddress().getHostAddress()).append("])");
        return new SMTPResponse(SMTPRetCode.MAIL_OK, response);
    }

    private boolean isValid(String argument) {
        String hostname = unquote(argument);

        // Without [] Guava attempt to parse IPV4
        return InetAddresses.isUriInetAddress(hostname)
            // Guava tries parsing IPv6 if and only if wrapped by []
            || InetAddresses.isUriInetAddress("[" + hostname + "]")
            || InternetDomainName.isValid(hostname);
    }

    private String unquote(String argument) {
        if (argument.startsWith("[") && argument.endsWith("]")) {
            return argument.substring(1, argument.length() - 1);
        }
        return argument;
    }

    @Override
    protected Response doFilterChecks(SMTPSession session, String command,
                                      String parameters) {
        session.resetState();

        if (parameters == null) {
            return DOMAIN_REQUIRED;
        } else {
            // store provided name
            session.setAttachment(SMTPSession.CURRENT_HELO_NAME, parameters, State.Connection);
            return null;
        }
    }

    @Override
    protected Class<HeloHook> getHookInterface() {
        return HeloHook.class;
    }


    @Override
    protected HookResult callHook(HeloHook rawHook, SMTPSession session, String parameters) {
        return rawHook.doHelo(session, parameters);
    }


}
