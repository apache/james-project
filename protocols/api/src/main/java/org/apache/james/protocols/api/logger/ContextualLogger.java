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
import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * {@link Logger} which adds context informations to the logged message.
 *
 */
public class ContextualLogger implements org.slf4j.Logger {

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

    public void debug(String arg0) {
        logger.debug(getText(arg0));
    }

    public void debug(String arg0, Throwable arg1) {
        logger.debug(getText(arg0), arg1);
    }

    public void error(String arg0) {
        logger.error(getText(arg0));
    }

    public void error(String arg0, Throwable arg1) {
        logger.error(getText(arg0), arg1);
    }

    public void info(String arg0) {
        logger.info(getText(arg0));
    }

    public void info(String arg0, Throwable arg1) {
        logger.info(getText(arg0), arg1);
    }

    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    public void trace(String arg0) {
        logger.trace(getText(arg0));
    }

    public void trace(String arg0, Throwable arg1) {
        logger.trace(getText(arg0), arg1);
    }

    public void warn(String arg0) {
        logger.warn(getText(arg0));
    }

    public void warn(String arg0, Throwable arg1) {
        logger.warn(getText(arg0), arg1);
    }

    @Override
    public String getName() {
        return logger.getName();
    }

    @Override
    public void trace(String format, Object arg) {
        logger.trace(getText(format), arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        logger.trace(getText(format), arg1, arg2);
    }

    @Override
    public void trace(String format, Object[] argArray) {
        logger.trace(getText(format), argArray);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return logger.isTraceEnabled(marker);
    }

    @Override
    public void trace(Marker marker, String msg) {
        logger.trace(marker, getText(msg));
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        logger.trace(marker, getText(format), arg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        logger.trace(marker, getText(format), arg1, arg2);
    }

    @Override
    public void trace(Marker marker, String format, Object[] argArray) {
        logger.trace(marker, getText(format), argArray);
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        logger.trace(marker, getText(msg), t);

    }

    @Override
    public void debug(String format, Object arg) {
        logger.debug(getText(format), arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        logger.debug(getText(format), arg1, arg2);
    }

    @Override
    public void debug(String format, Object[] argArray) {
        logger.debug(getText(format), argArray);
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return logger.isTraceEnabled(marker);
    }

    @Override
    public void debug(Marker marker, String msg) {
        logger.debug(marker, getText(msg));
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        logger.debug(marker, getText(format), arg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        logger.debug(marker, getText(format), arg1, arg2);
    }

    @Override
    public void debug(Marker marker, String format, Object[] argArray) {
        logger.debug(marker, getText(format), argArray);
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        logger.debug(marker, getText(msg), t);
    }

    @Override
    public void info(String format, Object arg) {
        logger.info(getText(format), arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        logger.info(getText(format), arg1, arg2);
    }

    @Override
    public void info(String format, Object[] argArray) {
        logger.info(getText(format), argArray);
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return logger.isInfoEnabled(marker);
    }

    @Override
    public void info(Marker marker, String msg) {
        logger.info(marker, getText(msg));
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        logger.info(marker, getText(format), arg);
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        logger.info(marker, getText(format), arg1, arg2);
    }

    @Override
    public void info(Marker marker, String format, Object[] argArray) {
        logger.info(marker, getText(format), argArray);
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        logger.info(marker, getText(msg), t);
    }

    @Override
    public void warn(String format, Object arg) {
        logger.warn(getText(format), arg);
    }

    @Override
    public void warn(String format, Object[] argArray) {
        logger.warn(getText(format), argArray);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        logger.warn(getText(format), arg1, arg2);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return logger.isWarnEnabled(marker);
    }

    @Override
    public void warn(Marker marker, String msg) {
        logger.warn(marker, getText(msg));
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        logger.warn(marker, getText(format), arg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        logger.warn(marker, getText(format), arg1, arg2);
    }

    @Override
    public void warn(Marker marker, String format, Object[] argArray) {
        logger.warn(marker, getText(format), argArray);
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        logger.warn(marker, getText(msg), t);
    }

    @Override
    public void error(String format, Object arg) {
        logger.error(getText(format), arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        logger.error(getText(format), arg1, arg2);
    }

    @Override
    public void error(String format, Object[] argArray) {
        logger.error(getText(format), argArray);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return logger.isErrorEnabled(marker);
    }

    @Override
    public void error(Marker marker, String msg) {
        logger.error(marker, getText(msg));
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        logger.error(marker, getText(format), arg);
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        logger.error(marker, getText(format), arg1, arg2);
    }

    @Override
    public void error(Marker marker, String format, Object[] argArray) {
        logger.error(marker, getText(format), argArray);
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        logger.error(marker, getText(msg), t);
    }
}
