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
package org.apache.james.transport.mailets.jsieve;

import org.apache.commons.logging.Log;
import org.apache.mailet.base.GenericMailet;

/**
 * Adapts commons logging to mailet logging.
 */
class CommonsLoggingAdapter implements Log {
    
    public static final int TRACE = 6;
    public static final int DEBUG = 5;
    public static final int INFO = 4;
    public static final int WARN = 3;
    public static final int ERROR = 2;
    public static final int FATAL = 1;
    
    private final GenericMailet mailet;
    private final int level;
    
    public CommonsLoggingAdapter(final GenericMailet mailet, final int level) {
        super();
        this.mailet = mailet;
        this.level = level;
    }

    public void debug(Object message) {
        if (isDebugEnabled()) {
            mailet.log(message == null ? "NULL" : message.toString());
        }
    }

    public void debug(Object message, Throwable t) {
        if (isDebugEnabled()) {
            mailet.log(message == null ? "NULL" : message.toString(), t);
        } 
    }

    public void error(Object message) {
        if (isErrorEnabled()) {
            mailet.log(message == null ? "NULL" : message.toString());
        }
    }

    public void error(Object message, Throwable t) {
        if (isErrorEnabled()) {
            mailet.log(message == null ? "NULL" : message.toString(), t);
        }
    }

    public void fatal(Object message) {
        if (isFatalEnabled()) {
            mailet.log(message == null ? "NULL" : message.toString());
        }
    }

    public void fatal(Object message, Throwable t) {
        if (isFatalEnabled()) {
            mailet.log(message == null ? "NULL" : message.toString(), t);
        }
    }

    public void info(Object message) {
        if (isInfoEnabled()) {
            mailet.log(message == null ? "NULL" : message.toString());
        }
    }

    public void info(Object message, Throwable t) {
        if (isInfoEnabled()) {
            mailet.log(message == null ? "NULL" : message.toString(), t);
        }
    }

    public boolean isDebugEnabled() {
        return level <= DEBUG;
    }

    public boolean isErrorEnabled() {
        return level <= ERROR;
    }

    public boolean isFatalEnabled() {
        return level <= FATAL;
    }

    public boolean isInfoEnabled() {
        return level <= INFO;
    }

    public boolean isTraceEnabled() {
        return level <= TRACE;
    }

    public boolean isWarnEnabled() {
        return level <= WARN;
    }

    public void trace(Object message) {
        if (isTraceEnabled()) {
            mailet.log(message == null ? "NULL" : message.toString());
        }
    }

    public void trace(Object message, Throwable t) {
        if (isTraceEnabled()) {
            mailet.log(message == null ? "NULL" : message.toString(), t);
        }
    }

    public void warn(Object message) {
        if (isWarnEnabled()) {
            mailet.log(message == null ? "NULL" : message.toString());
        }
    }

    public void warn(Object message, Throwable t) {
        if (isWarnEnabled()) {
            mailet.log(message == null ? "NULL" : message.toString(), t);
        }
    }
    
    
}
