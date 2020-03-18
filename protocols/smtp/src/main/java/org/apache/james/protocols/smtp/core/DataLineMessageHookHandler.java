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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.ExtensibleHandler;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookResultHook;
import org.apache.james.protocols.smtp.hook.MessageHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class handles the actual calling of the {@link MessageHook} implementations to queue the message. If no {@link MessageHook} return OK or DECLINED it will write back an
 * error to the client to report the problem while trying to queue the message
 */
public class DataLineMessageHookHandler implements DataLineFilter, ExtensibleHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataLineMessageHookHandler.class);

    private static final Response ERROR_PROCESSING_MESSAGE = new SMTPResponse(SMTPRetCode.LOCAL_ERROR,DSNStatus.getStatus(DSNStatus.TRANSIENT,
            DSNStatus.UNDEFINED_STATUS) + " Error processing message").immutable();
    
    private List<?> messageHandlers;
    
    private List<?> rHooks;

    @Override
    public Response onLine(SMTPSession session, ByteBuffer line, LineHandler<SMTPSession> next) {
        MailEnvelope env = session.getAttachment(DataCmdHandler.MAILENV, ProtocolSession.State.Transaction)
            .orElseThrow(() -> new RuntimeException("'" + DataCmdHandler.MAILENV.asString() + "' has not been filled."));

        OutputStream out = getMessageOutputStream(env);
        try {
            // 46 is "."
            // Stream terminated            
            int c = line.get();
            if (line.remaining() == 2 && c == 46) {
                out.flush();
                out.close();
                
                Response response = processExtensions(session, env);
                session.popLineHandler();
                session.resetState();
                return response;
                
            // DotStuffing.
            } else if (c == 46 && line.get() == 46) {
                byte[] bline = readBytes(line);
                out.write(bline,1,bline.length - 1);
            // Standard write
            } else {
                // TODO: maybe we should handle the Header/Body recognition here
                // and if needed let a filter to cache the headers to apply some
                // transformation before writing them to output.
                out.write(readBytes(line));
            }
            out.flush();
        } catch (IOException e) {
            LOGGER.error("Unknown error occurred while processing DATA.", e);
            
            session.resetState();
            return ERROR_PROCESSING_MESSAGE;
        }
        return null;
    }

    private OutputStream getMessageOutputStream(MailEnvelope env) {
        try {
            return env.getMessageOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] readBytes(ByteBuffer line) {
        line.rewind();
        byte[] bline;
        if (line.hasArray()) {
            bline = line.array();
        } else {
            bline = new byte[line.remaining()];
            line.get(bline);
        }
        return bline;
    }

    protected Response processExtensions(SMTPSession session, MailEnvelope mail) {
       

        if (messageHandlers != null) {
            for (Object messageHandler : messageHandlers) {
                MessageHook rawHandler = (MessageHook) messageHandler;
                LOGGER.debug("executing message handler {}", rawHandler);

                long start = System.currentTimeMillis();
                HookResult hRes = rawHandler.onMessage(session, mail);
                long executionTime = System.currentTimeMillis() - start;

                if (rHooks != null) {
                    for (Object rHook : rHooks) {
                        LOGGER.debug("executing hook {}", rHook);
                        hRes = ((HookResultHook) rHook).onHookResult(session, hRes, executionTime, rawHandler);
                    }
                }

                SMTPResponse response = AbstractHookableCmdHandler.calcDefaultSMTPResponse(hRes);

                // if the response is received, stop processing of command
                // handlers
                if (response != null) {
                    return response;
                }
            }

            // Not queue the message!
            return AbstractHookableCmdHandler.calcDefaultSMTPResponse(HookResult.DECLINED);
        }
        
        return null;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void wireExtensions(Class interfaceName, List extension) throws WiringException {
        if (MessageHook.class.equals(interfaceName)) {
            this.messageHandlers = extension;
            checkMessageHookCount(messageHandlers);
        } else if (HookResultHook.class.equals(interfaceName)) {
            this.rHooks = extension;
        }
    }

    protected void checkMessageHookCount(List<?> messageHandlers) throws WiringException {
        if (messageHandlers.size() == 0) {
            throw new WiringException("No messageHandler configured");
        }
    }
    
    @Override
    public List<Class<?>> getMarkerInterfaces() {
        List<Class<?>> classes = new LinkedList<>();
        classes.add(MessageHook.class);
        classes.add(HookResultHook.class);
        return classes;
    }

}