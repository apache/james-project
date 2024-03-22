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
import java.util.Optional;

import jakarta.inject.Inject;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class which Handle hook-aware CommanHandler.
 */
public abstract class AbstractHookableCmdHandler<HookT extends org.apache.james.protocols.smtp.hook.Hook> implements CommandHandler<SMTPSession>, ExtensibleHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractHookableCmdHandler.class);

    protected final MetricFactory metricFactory;
    private List<HookT> hooks;
    private List<HookResultHook> rHooks;

    @Inject
    public AbstractHookableCmdHandler(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    @Override
    public Response onCommand(SMTPSession session, Request request) {
        TimeMetric timeMetric = timer(request);
        String command = request.getCommand();
        String parameters = request.getArgument();

        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.ACTION, command)
                     .build()) {
            Response response = doFilterChecks(session, command, parameters);
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
            throw new RuntimeException(e);
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    protected TimeMetric timer(Request request) {
        return metricFactory.timer("SMTP-" + request.getCommand().toLowerCase(Locale.US));
    }

    /**
     * Process all hooks for the given command
     * 
     * @param session
     *            the SMTPSession object
     * @param command
     *            the command
     * @param parameters
     *            the parameters
     * @return SMTPResponse
     */
    private Response processHooks(SMTPSession session, String command,
            String parameters) {
        List<HookT> hooks = getHooks();
        if (hooks != null) {
            int count = hooks.size();
            int i = 0;
            while (i < count) {
                HookT rawHook = hooks.get(i);
                LOGGER.debug("executing hook {}", rawHook.getClass().getName());
                long start = System.currentTimeMillis();

                HookResult hRes = callHook(rawHook, session, parameters);
                long executionTime = System.currentTimeMillis() - start;

                if (rHooks != null) {
                    for (HookResultHook rHook : rHooks) {
                        LOGGER.debug("executing hook {}", rHook);
                        hRes = rHook.onHookResult(session, hRes, executionTime, rawHook);
                    }
                }

                // call the core cmd if we receive a ok return code of the hook so no other hooks are executed
                if (hRes.getResult().getAction() == HookReturnCode.Action.OK) {
                    final Response response = doCoreCmd(session, command, parameters);
                    if (hRes.getResult().isDisconnected()) {
                        return new Response() {

                            @Override
                            public boolean isEndSession() {
                                return true;
                            }

                            @Override
                            public String getRetCode() {
                                return response.getRetCode();
                            }

                            @Override
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
    protected abstract HookResult callHook(HookT rawHook, SMTPSession session, String parameters);

    /**
     * Convert the HookResult to SMTPResponse using default values. Should be override for using own values
     * 
     * @param result HookResult
     * @return SMTPResponse
     */
    public static SMTPResponse calcDefaultSMTPResponse(HookResult result) {
        if (result != null) {
            HookReturnCode returnCode = result.getResult();

            String smtpReturnCode = Optional
                .ofNullable(result.getSmtpRetCode())
                .or(() -> retrieveDefaultSmtpReturnCode(returnCode))
                .orElse(null);

            String smtpDescription = Optional
                .ofNullable(result.getSmtpDescription())
                .or(() -> retrieveDefaultSmtpDescription(returnCode))
                .orElse(null);

            if (canBeConvertedToSmtpAnswer(returnCode)) {

                SMTPResponse response = new SMTPResponse(smtpReturnCode, smtpDescription);
                if (returnCode.isDisconnected()) {
                    response.setEndSession(true);
                }
                return response;
            }
        }
        return null;
    }

    public static boolean canBeConvertedToSmtpAnswer(HookReturnCode returnCode) {
        return HookReturnCode.Action.ACTIVE_ACTIONS
            .contains(returnCode.getAction()) || returnCode.isDisconnected();
    }

    private static Optional<String> retrieveDefaultSmtpDescription(HookReturnCode returnCode) {
        switch (returnCode.getAction()) {
            case DENY:
                return Optional.of("Email rejected");
            case DENYSOFT:
                return Optional.of("Temporary problem. Please try again later");
            case OK:
                return Optional.of("Command accepted");
            case DECLINED:
            case NONE:
                break;
        }
        if (returnCode.isDisconnected()) {
            return Optional.of("Server disconnected");
        }
        return Optional.empty();
    }

    private static Optional<String> retrieveDefaultSmtpReturnCode(HookReturnCode returnCode) {
        switch (returnCode.getAction()) {
            case DENY:
                return Optional.of(SMTPRetCode.TRANSACTION_FAILED);
            case DENYSOFT:
                return Optional.of(SMTPRetCode.LOCAL_ERROR);
            case OK:
                return Optional.of(SMTPRetCode.MAIL_OK);
            case DECLINED:
            case NONE:
                break;
        }
        if (returnCode.isDisconnected()) {
            return Optional.of(SMTPRetCode.TRANSACTION_FAILED);
        }
        return Optional.empty();
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
    

    @Override
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
    protected abstract Class<HookT> getHookInterface();

    @Override
    @SuppressWarnings("unchecked")
    public void wireExtensions(Class<?> interfaceName, List<?> extension) {
        if (getHookInterface().equals(interfaceName)) {
            this.hooks = (List<HookT>) extension;
        } else if (HookResultHook.class.equals(interfaceName)) {
            this.rHooks = (List<HookResultHook>) extension;
        }

    }

    /**
     * Return a list which holds all hooks for the cmdHandler
     * 
     * @return list containing all hooks for the cmd handler
     */
    protected List<HookT> getHooks() {
        return hooks;
    }

}
