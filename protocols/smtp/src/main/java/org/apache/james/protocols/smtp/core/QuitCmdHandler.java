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

import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.QuitHook;

import com.google.common.collect.ImmutableSet;

/**
 * Handles QUIT command
 */
public class QuitCmdHandler extends AbstractHookableCmdHandler<QuitHook> {

    /**
     * The name of the command handled by the command handler
     */
    private static final Collection<String> COMMANDS = ImmutableSet.of("QUIT");

    private static final Response SYNTAX_ERROR;
    static {
        SMTPResponse response = new SMTPResponse(
                SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED, DSNStatus
                .getStatus(DSNStatus.PERMANENT,
                        DSNStatus.DELIVERY_INVALID_ARG)
                + " Unexpected argument provided with QUIT command");
        response.setEndSession(true);
        SYNTAX_ERROR = response.immutable();
    }

    @Inject
    public QuitCmdHandler(MetricFactory metricFactory) {
        super(metricFactory);
    }


    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }

    /**
     * Handler method called upon receipt of a QUIT command. This method informs
     * the client that the connection is closing.
     * 
     * @param session
     *            SMTP session object
     * @param argument
     *            the argument passed in with the command by the SMTP client
     */
    private Response doQUIT(SMTPSession session, String argument) {
        if ((argument == null) || (argument.length() == 0)) {
            StringBuilder response = new StringBuilder();
            response.append(
                    DSNStatus.getStatus(DSNStatus.SUCCESS,
                            DSNStatus.UNDEFINED_STATUS)).append(" ").append(
                    session.getConfiguration().getHelloName()).append(
                    " Service closing transmission channel");
            SMTPResponse ret = new SMTPResponse(SMTPRetCode.SYSTEM_QUIT, response);
            ret.setEndSession(true);
            return ret;
        } else {
            return SYNTAX_ERROR;
        }
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
        return doQUIT(session, parameters);
    }

    /**
     * @see org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler#doFilterChecks(org.apache.james.protocols.smtp.SMTPSession,
     *      java.lang.String, java.lang.String)
     */
    protected Response doFilterChecks(SMTPSession session, String command,
            String parameters) {
        return null;
    }

    /**
     * @see org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler#getHookInterface()
     */
    protected Class<QuitHook> getHookInterface() {
        return QuitHook.class;
    }

    /**
     * {@inheritDoc}
     */
    protected HookResult callHook(QuitHook rawHook, SMTPSession session, String parameters) {
        return rawHook.doQuit(session);
    }

}
