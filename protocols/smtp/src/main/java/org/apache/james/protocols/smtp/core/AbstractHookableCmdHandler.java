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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.api.handler.ExtensibleHandler;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookResultHook;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.util.MDCBuilder;

import com.google.common.base.Throwables;

/**
 * Abstract class which Handle hook-aware CommanHandler.
 * 
 */
public abstract class AbstractHookableCmdHandler<Hook extends org.apache.james.protocols.smtp.hook.Hook> implements CommandHandler<SMTPSession>, ExtensibleHandler {

    private final MetricFactory metricFactory;
    private List<Hook> hooks;
    private List<HookResultHook> rHooks;

    @Inject
    public AbstractHookableCmdHandler(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    /**
     * Handle command processing
     * 
     * @see org.apache.james.protocols.api.handler.CommandHandler
     * #onCommand(org.apache.james.protocols.api.ProtocolSession, Request)
     */
    public Response onCommand(SMTPSession session, Request request) {
        TimeMetric timeMetric = metricFactory.timer("SMTP-" + request.getCommand().toLowerCase(Locale.US));
        String command = request.getCommand();
        String parameters = request.getArgument();
        Response response = doFilterChecks(session, command, parameters);

        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.ACTION, command)
                     .build()) {
            if (response == null) {

                response = processHooks(session, command, parameters);
                if (response == null) {
                    return doCoreCmd(session, command, parameters);
                } else {
                    return response;
                }
            } else {
                return response;
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            timeMetric.stopAndPublish();
        }

    }

    /**
     * Process all hooks for the given command
     * 
     * @param session
     *            the SMTPSession object
     * @param command
     *            the command
     * @param parameters
     *            the paramaters
     * @return SMTPResponse
     */
    private Response processHooks(SMTPSession session, String command,
            String parameters) {
        List<Hook> hooks = getHooks();
        if (hooks != null) {
            int count = hooks.size();
            int i = 0;
            while (i < count) {
                Hook rawHook = hooks.get(i);
                session.getLogger().debug("executing hook " + rawHook.getClass().getName());
                long start = System.currentTimeMillis();

                HookResult hRes = callHook(rawHook, session, parameters);
                long executionTime = System.currentTimeMillis() - start;

                if (rHooks != null) {
                    for (HookResultHook rHook : rHooks) {
                        session.getLogger().debug("executing hook " + rHook);
                        hRes = rHook.onHookResult(session, hRes, executionTime, rawHook);
                    }
                }

                // call the core cmd if we receive a ok return code of the hook so no other hooks are executed
                if ((hRes.getResult() & HookReturnCode.OK) == HookReturnCode.OK) {
                    final Response response = doCoreCmd(session, command, parameters);
                    if ((hRes.getResult() & HookReturnCode.DISCONNECT) == HookReturnCode.DISCONNECT) {
                        return new Response() {

                            /*
                             * (non-Javadoc)
                             * @see org.apache.james.protocols.api.Response#isEndSession()
                             */
                            public boolean isEndSession() {
                                return true;
                            }

                            /*
                             * (non-Javadoc)
                             * @see org.apache.james.protocols.api.Response#getRetCode()
                             */
                            public String getRetCode() {
                                return response.getRetCode();
                            }

                            /*
                             * (non-Javadoc)
                             * @see org.apache.james.protocols.api.Response#getLines()
                             */
                            public List<CharSequence> getLines() {
                                return response.getLines();
                            }
                        };
                    }
                    return response;
                } else {
                    SMTPResponse res = calcDefaultSMTPResponse(hRes);
                    if (res != null) {
                        return res;
                    }
                }
                i++;
            }
        }
        return null;
    }

    /**
     * Must be implemented by hookable cmd handlers to make the effective call to an hook.
     * 
     * @param rawHook the hook
     * @param session the session
     * @param parameters the parameters
     * @return the HookResult, will be calculated using HookResultToSMTPResponse.
     */
    protected abstract HookResult callHook(Hook rawHook, SMTPSession session, String parameters);

    /**
     * Convert the HookResult to SMTPResponse using default values. Should be override for using own values
     * 
     * @param result HookResult
     * @return SMTPResponse
     */
    public static SMTPResponse calcDefaultSMTPResponse(HookResult result) {
        if (result != null) {
            int rCode = result.getResult();
            String smtpRetCode = result.getSmtpRetCode();
            String smtpDesc = result.getSmtpDescription();
    
            if ((rCode &HookReturnCode.DENY) == HookReturnCode.DENY) {
                if (smtpRetCode == null)
                    smtpRetCode = SMTPRetCode.TRANSACTION_FAILED;
                if (smtpDesc == null)
                    smtpDesc = "Email rejected";
    
                SMTPResponse response =  new SMTPResponse(smtpRetCode, smtpDesc);
                if ((rCode & HookReturnCode.DISCONNECT) == HookReturnCode.DISCONNECT) {
                    response.setEndSession(true);
                }
                return response;
            } else if ((rCode & HookReturnCode.DENYSOFT) == HookReturnCode.DENYSOFT) {
                if (smtpRetCode == null)
                    smtpRetCode = SMTPRetCode.LOCAL_ERROR;
                if (smtpDesc == null)
                    smtpDesc = "Temporary problem. Please try again later";
    
                SMTPResponse response = new SMTPResponse(smtpRetCode, smtpDesc);
                if ((rCode & HookReturnCode.DISCONNECT) == HookReturnCode.DISCONNECT) {
                    response.setEndSession(true);
                }
                return response;
            } else if ((rCode & HookReturnCode.OK) == HookReturnCode.OK) {
                if (smtpRetCode == null)
                    smtpRetCode = SMTPRetCode.MAIL_OK;
                if (smtpDesc == null)
                    smtpDesc = "Command accepted";
    
                SMTPResponse response = new SMTPResponse(smtpRetCode, smtpDesc);
                if ((rCode & HookReturnCode.DISCONNECT) == HookReturnCode.DISCONNECT) {
                    response.setEndSession(true);
                }
                return response;
            } else if ((rCode & HookReturnCode.DISCONNECT) == HookReturnCode.DISCONNECT) {
                if (smtpRetCode == null)
                    smtpRetCode = SMTPRetCode.TRANSACTION_FAILED;
                if (smtpDesc == null)
                    smtpDesc = "Server disconnected";

                SMTPResponse response =  new SMTPResponse(smtpRetCode, smtpDesc);
                response.setEndSession(true);
                return response;
            } else {
                // Return null as default
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Execute Syntax checks and return a SMTPResponse if a syntax error was
     * detected, otherwise null.
     * 
     * @param session
     * @param command
     * @param parameters
     * @return smtp response if a syntax error was detected, otherwise <code>null</code>
     */
    protected abstract Response doFilterChecks(SMTPSession session,
            String command, String parameters);

    /**
     * Execute the core commandHandling.
     * 
     * @param session
     * @param command
     * @param parameters
     * @return smtp response
     */
    protected abstract Response doCoreCmd(SMTPSession session,
            String command, String parameters);
    

    /**
     * @see org.apache.james.protocols.api.handler.ExtensibleHandler#getMarkerInterfaces()
     */
    public List<Class<?>> getMarkerInterfaces() {
        List<Class<?>> classes = new ArrayList<>(2);
        classes.add(getHookInterface());
        classes.add(HookResultHook.class);
        return classes;
    }

    /**
     * Return the interface which hooks need to implement to hook in
     * 
     * @return interface
     */
    protected abstract Class<Hook> getHookInterface();

    /**
     * @see org.apache.james.protocols.api.handler.ExtensibleHandler#wireExtensions(java.lang.Class,
     *      java.util.List)
     */
    @SuppressWarnings("unchecked")
	public void wireExtensions(Class<?> interfaceName, List<?> extension) {
        if (getHookInterface().equals(interfaceName)) {
            this.hooks = (List<Hook>) extension;
        } else if (HookResultHook.class.equals(interfaceName)) {
            this.rHooks = (List<HookResultHook>) extension;
        }

    }

    /**
     * Return a list which holds all hooks for the cmdHandler
     * 
     * @return list containing all hooks for the cmd handler
     */
    protected List<Hook> getHooks() {
        return hooks;
    }

}
