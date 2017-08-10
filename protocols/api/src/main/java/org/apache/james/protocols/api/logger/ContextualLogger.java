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

package org.apache.james.protocols.api.logger;

import java.util.function.Supplier;

import org.apache.james.protocols.api.ProtocolSession;

/**
 * {@link Logger} which adds context informations to the logged message.
 *
 */
public class ContextualLogger implements Logger {

    private final Supplier<String> userSupplier;
    private final String sessionId;
    private final Logger logger;

    public ContextualLogger(final ProtocolSession session, Logger logger) {
        this(session::getUser, session.getSessionID(), logger);
    }

    public ContextualLogger(Supplier<String> userSupplier, String sessionId, Logger logger) {
        this.userSupplier = userSupplier;
        this.sessionId = sessionId;
        this.logger = logger;
    }

    private String getText(String str) {
        String user = userSupplier.get();
        StringBuilder sb = new StringBuilder();
        sb.append("Id='").append(sessionId);
        sb.append("' User='");
        if (user != null) {
            sb.append(user);
        }
        sb.append("' ").append(str);
        return sb.toString();
    }
    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.logger.Logger#debug(java.lang.String)
     */
    public void debug(String arg0) {
        logger.debug(getText(arg0));
    }


    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.logger.Logger#debug(java.lang.String, java.lang.Throwable)
     */
    public void debug(String arg0, Throwable arg1) {
        logger.debug(getText(arg0), arg1);
    }


    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.logger.Logger#error(java.lang.String)
     */
    public void error(String arg0) {
        logger.error(getText(arg0));
    }


    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.logger.Logger#error(java.lang.String, java.lang.Throwable)
     */
    public void error(String arg0, Throwable arg1) {
        logger.error(getText(arg0), arg1);
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.logger.Logger#info(java.lang.String)
     */
    public void info(String arg0) {
        logger.info(getText(arg0));
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.logger.Logger#info(java.lang.String, java.lang.Throwable)
     */
    public void info(String arg0, Throwable arg1) {
        logger.info(getText(arg0), arg1);
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.logger.Logger#isDebugEnabled()
     */
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.logger.Logger#isErrorEnabled()
     */
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.logger.Logger#isInfoEnabled()
     */
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.logger.Logger#isTraceEnabled()
     */
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.logger.Logger#isWarnEnabled()
     */
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.logger.Logger#trace(java.lang.String)
     */
    public void trace(String arg0) {
        logger.trace(getText(arg0));
    }
    
    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.logger.Logger#trace(java.lang.String, java.lang.Throwable)
     */
    public void trace(String arg0, Throwable arg1) {
        logger.trace(getText(arg0), arg1);
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.logger.Logger#warn(java.lang.String)
     */
    public void warn(String arg0) {
        logger.warn(getText(arg0));
    }


    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.logger.Logger#warn(java.lang.String, java.lang.Throwable)
     */
    public void warn(String arg0, Throwable arg1) {
        logger.warn(getText(arg0), arg1);
    }

}
