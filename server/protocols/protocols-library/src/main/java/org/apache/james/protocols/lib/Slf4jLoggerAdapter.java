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

package org.apache.james.protocols.lib;

import org.apache.james.protocols.api.logger.Logger;
import org.slf4j.Marker;

/**
 * Adapter class for SLF4J
 *
 */
public class Slf4jLoggerAdapter implements org.slf4j.Logger {

    private final Logger logger;

    public Slf4jLoggerAdapter(Logger logger) {
        this.logger = logger;
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

    public void trace(String message) {
        logger.trace(message);
    }

    public void trace(String message, Throwable t) {
        logger.trace(message, t);
    }

    public void debug(String message) {
        logger.debug(message);
    }

    public void debug(String message, Throwable t) {
        logger.debug(message, t);
    }

    public void info(String message) {
        logger.info(message);
    }

    public void info(String message, Throwable t) {
        logger.info(message, t);
    }

    public void warn(String message) {
        logger.warn(message);
    }

    public void warn(String message, Throwable t) {
        logger.warn(message, t);
    }

    public void error(String message) {
        logger.error(message);
    }

    public void error(String message, Throwable t) {
        logger.error(message, t);
    }

    public void debug(String arg0, Object arg1) {
        throw new UnsupportedOperationException();
        
    }

    public void debug(String arg0, Object[] arg1) {
        throw new UnsupportedOperationException();
        
    }

    public void debug(Marker arg0, String arg1) {
        throw new UnsupportedOperationException();
        
    }

    public void debug(String arg0, Object arg1, Object arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void debug(Marker arg0, String arg1, Object arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void debug(Marker arg0, String arg1, Object[] arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void debug(Marker arg0, String arg1, Throwable arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void debug(Marker arg0, String arg1, Object arg2, Object arg3) {
        throw new UnsupportedOperationException();
        
    }

    public void error(String arg0, Object arg1) {
        throw new UnsupportedOperationException();
        
    }

    public void error(String arg0, Object[] arg1) {
        throw new UnsupportedOperationException();
        
    }

    public void error(Marker arg0, String arg1) {
        throw new UnsupportedOperationException();
        
    }

    public void error(String arg0, Object arg1, Object arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void error(Marker arg0, String arg1, Object arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void error(Marker arg0, String arg1, Object[] arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void error(Marker arg0, String arg1, Throwable arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void error(Marker arg0, String arg1, Object arg2, Object arg3) {
        throw new UnsupportedOperationException();
        
    }

    public String getName() {
        return logger.getClass().getCanonicalName();
    }

    public void info(String arg0, Object arg1) {
        throw new UnsupportedOperationException();
        
    }

    public void info(String arg0, Object[] arg1) {
        throw new UnsupportedOperationException();
        
    }

    public void info(Marker arg0, String arg1) {
        throw new UnsupportedOperationException();
        
    }

    public void info(String arg0, Object arg1, Object arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void info(Marker arg0, String arg1, Object arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void info(Marker arg0, String arg1, Object[] arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void info(Marker arg0, String arg1, Throwable arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void info(Marker arg0, String arg1, Object arg2, Object arg3) {
        throw new UnsupportedOperationException();
        
    }

    public boolean isDebugEnabled(Marker arg0) {
        throw new UnsupportedOperationException();
    }

    public boolean isErrorEnabled(Marker arg0) {
        throw new UnsupportedOperationException();
    }

    public boolean isInfoEnabled(Marker arg0) {
        throw new UnsupportedOperationException();
    }

    public boolean isTraceEnabled(Marker arg0) {
        throw new UnsupportedOperationException();
    }

    public boolean isWarnEnabled(Marker arg0) {
        throw new UnsupportedOperationException();
    }

    public void trace(String arg0, Object arg1) {
        throw new UnsupportedOperationException();
        
    }

    public void trace(String arg0, Object[] arg1) {
        throw new UnsupportedOperationException();
        
    }

    public void trace(Marker arg0, String arg1) {
        throw new UnsupportedOperationException();
        
    }

    public void trace(String arg0, Object arg1, Object arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void trace(Marker arg0, String arg1, Object arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void trace(Marker arg0, String arg1, Object[] arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void trace(Marker arg0, String arg1, Throwable arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void trace(Marker arg0, String arg1, Object arg2, Object arg3) {
        throw new UnsupportedOperationException();
        
    }

    public void warn(String arg0, Object arg1) {
        throw new UnsupportedOperationException();
        
    }

    public void warn(String arg0, Object[] arg1) {
        throw new UnsupportedOperationException();
        
    }

    public void warn(Marker arg0, String arg1) {
        throw new UnsupportedOperationException();
        
    }

    public void warn(String arg0, Object arg1, Object arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void warn(Marker arg0, String arg1, Object arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void warn(Marker arg0, String arg1, Object[] arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void warn(Marker arg0, String arg1, Throwable arg2) {
        throw new UnsupportedOperationException();
        
    }

    public void warn(Marker arg0, String arg1, Object arg2, Object arg3) {
        throw new UnsupportedOperationException();
        
    }

}
