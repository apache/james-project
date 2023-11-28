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

import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HeloHook;
import org.apache.james.protocols.smtp.hook.HookResult;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * Handles EHLO command
 */
public class EhloCmdHandler extends AbstractHookableCmdHandler<HeloHook> implements EhloExtension {

    /**
     * The name of the command handled by the command handler
     */
    private static final String COMMAND_NAME = "EHLO";
    private static final Collection<String> COMMANDS = ImmutableSet.of(COMMAND_NAME);
    // see http://issues.apache.org/jira/browse/JAMES-419
    private static final List<String> ESMTP_FEATURES = ImmutableList.of("PIPELINING", "ENHANCEDSTATUSCODES", "8BITMIME");
    private static final Response DOMAIN_ADDRESS_REQUIRED = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " Domain address required: " + COMMAND_NAME).immutable();
    
    private List<EhloExtension> ehloExtensions;

    @Inject
    public EhloCmdHandler(MetricFactory metricFactory) {
        super(metricFactory);
    }

    /**
     * Handler method called upon receipt of a EHLO command. Responds with a
     * greeting and informs the client whether client authentication is
     * required.
     * 
     * @param session
     *            SMTP session object
     * @param argument
     *            the argument passed in with the command by the SMTP client
     */
    private Response doEHLO(SMTPSession session, String argument) {
        SMTPResponse resp = new SMTPResponse(SMTPRetCode.MAIL_OK, new StringBuilder(session.getConfiguration().getHelloName()).append(" Hello ").append(argument)
                .append(" [")
                .append(session.getRemoteAddress().getAddress().getHostAddress()).append("])"));
        
        session.setAttachment(SMTPSession.CURRENT_HELO_MODE,
                COMMAND_NAME, State.Connection);

        processExtensions(session, resp);


 
        return resp;

    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    public List<Class<?>> getMarkerInterfaces() {
        List<Class<?>> classes = super.getMarkerInterfaces();
        classes.add(EhloExtension.class);
        return classes;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void wireExtensions(Class<?> interfaceName, List<?> extension) {
        super.wireExtensions(interfaceName, extension);
        if (EhloExtension.class.equals(interfaceName)) {
            this.ehloExtensions = (List<EhloExtension>) extension;
        }
    }

    /**
     * Process the ehloExtensions
     * 
     * @param session SMTPSession 
     * @param resp SMTPResponse
     */
    private void processExtensions(SMTPSession session, SMTPResponse resp) {
        if (ehloExtensions != null) {
            for (EhloExtension ehloExtension : ehloExtensions) {
                List<String> lines = ehloExtension.getImplementedEsmtpFeatures(session);
                if (lines != null) {
                    for (String line : lines) {
                        resp.appendLine(line);
                    }
                }
            }
        }
    }

    @Override
    protected Response doCoreCmd(SMTPSession session, String command,
                                 String parameters) {
        return doEHLO(session, parameters);
    }

    @Override
    protected Response doFilterChecks(SMTPSession session, String command,
                                      String parameters) {
        session.resetState();

        if (parameters == null) {
            return DOMAIN_ADDRESS_REQUIRED;
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



    @Override
    public List<String> getImplementedEsmtpFeatures(SMTPSession session) {
        ImmutableSet<String> esmtpFeatures = ImmutableSet.<String>builder()
            .addAll(ESMTP_FEATURES)
            .addAll(getHooks().stream()
                .flatMap(heloHook -> heloHook.implementedEsmtpFeatures(session).stream())
                .collect(ImmutableList.toImmutableList()))
            .build();

        return ImmutableList.copyOf(
            Sets.difference(esmtpFeatures,
                session.disabledFeatures()));
    }

}
