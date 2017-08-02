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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.api.handler.ExtensibleHandler;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.MailEnvelopeImpl;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;


/**
  * handles DATA command
 */
public class DataCmdHandler implements CommandHandler<SMTPSession>, ExtensibleHandler {

    private static final Response NO_RECIPIENT = new SMTPResponse(SMTPRetCode.BAD_SEQUENCE, DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.DELIVERY_OTHER)+" No recipients specified").immutable();
    private static final Response NO_SENDER = new SMTPResponse(SMTPRetCode.BAD_SEQUENCE, DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.DELIVERY_OTHER)+" No sender specified").immutable();
    private static final Response UNEXPECTED_ARG = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED, DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.DELIVERY_INVALID_ARG)+" Unexpected argument provided with DATA command").immutable();
    private static final Response DATA_READY = new SMTPResponse(SMTPRetCode.DATA_READY, "Ok Send data ending with <CRLF>.<CRLF>").immutable();
    private static final Collection<String> COMMANDS = Collections.unmodifiableCollection(Arrays.asList("DATA"));

    public static final class DataConsumerLineHandler implements LineHandler<SMTPSession> {

        public SMTPResponse onLine(SMTPSession session, ByteBuffer line) {
            // Discard everything until the end of DATA session
            if (line.remaining() == 3 && line.get() == 46) {
                session.popLineHandler();
            }
            return null;
        }

        @Override
        public void init(Configuration config) throws ConfigurationException {

        }

        @Override
        public void destroy() {

        }
    }

    public static final class DataLineFilterWrapper implements LineHandler<SMTPSession> {

        private final DataLineFilter filter;
        private final LineHandler<SMTPSession> next;
        
        public DataLineFilterWrapper(DataLineFilter filter, LineHandler<SMTPSession> next) {
            this.filter = filter;
            this.next = next;
        }
        

        /*
         * (non-Javadoc)
         * @see org.apache.james.protocols.api.handler.LineHandler#onLine(org.apache.james.protocols.api.ProtocolSession, java.nio.ByteBuffer)
         */
        public Response onLine(SMTPSession session, ByteBuffer line) {
            line.rewind();
            return filter.onLine(session, line, next);
        }

        @Override
        public void init(Configuration config) throws ConfigurationException {

        }

        @Override
        public void destroy() {

        }
    }
   
    public final static String MAILENV = "MAILENV";

    private final MetricFactory metricFactory;

    @Inject
    public DataCmdHandler(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    private LineHandler<SMTPSession> lineHandler;

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }

    /**
     * process DATA command
     *
     */
    public Response onCommand(SMTPSession session, Request request) {
        TimeMetric timeMetric = metricFactory.timer("SMTP-" + request.getCommand());
        session.stopDetectingCommandInjection();
        try {
            String parameters = request.getArgument();
            Response response = doDATAFilter(session, parameters);

            if (response == null) {
                return doDATA(session, parameters);
            } else {
                return response;
            }
        } finally {
            timeMetric.stopAndPublish();
            session.needsCommandInjectionDetection();
        }
    }


    /**
     * Handler method called upon receipt of a DATA command.
     * Reads in message data, creates header, and delivers to
     * mail server service for delivery.
     *
     * @param session SMTP session object
     * @param argument the argument passed in with the command by the SMTP client
     */
    @SuppressWarnings("unchecked")
    protected Response doDATA(SMTPSession session, String argument) {
        MailEnvelope env = createEnvelope(session, (MailAddress) session.getAttachment(SMTPSession.SENDER,ProtocolSession.State.Transaction), new ArrayList<>((Collection<MailAddress>) session.getAttachment(SMTPSession.RCPT_LIST, ProtocolSession.State.Transaction)));
        session.setAttachment(MAILENV, env,ProtocolSession.State.Transaction);
        session.pushLineHandler(lineHandler);
        
        return DATA_READY;
    }
    
    protected MailEnvelope createEnvelope(SMTPSession session, MailAddress sender, List<MailAddress> recipients) {
        MailEnvelopeImpl env = new MailEnvelopeImpl();
        env.setRecipients(recipients);
        env.setSender(sender);
        return env;
    }
    
    
    /**
     * @see org.apache.james.protocols.api.handler.CommandHandler#getImplCommands()
     */
    public Collection<String> getImplCommands() {
    	return COMMANDS;
    }


    /**
     * @see org.apache.james.protocols.api.handler.ExtensibleHandler#getMarkerInterfaces()
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public List getMarkerInterfaces() {
        List classes = new LinkedList();
        classes.add(DataLineFilter.class);
        return classes;
    }


    /**
     * @see org.apache.james.protocols.api.handler.ExtensibleHandler#wireExtensions(java.lang.Class, java.util.List)
     */
    @SuppressWarnings("rawtypes")
    public void wireExtensions(Class interfaceName, List extension) throws WiringException {
        if (DataLineFilter.class.equals(interfaceName)) {

            LineHandler<SMTPSession> lineHandler = new DataConsumerLineHandler();
            for (int i = extension.size() - 1; i >= 0; i--) {
                lineHandler = new DataLineFilterWrapper((DataLineFilter) extension.get(i), lineHandler);
            }

            this.lineHandler = lineHandler;
        }
    }

    protected Response doDATAFilter(SMTPSession session, String argument) {
        if ((argument != null) && (argument.length() > 0)) {
            return UNEXPECTED_ARG;
        }
        if (session.getAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction) == null) {
            return NO_SENDER;
        } else if (session.getAttachment(SMTPSession.RCPT_LIST, ProtocolSession.State.Transaction) == null) {
            return NO_RECIPIENT;
        }
        return null;
    }

    protected LineHandler<SMTPSession> getLineHandler() {
        return lineHandler;
    }

}
