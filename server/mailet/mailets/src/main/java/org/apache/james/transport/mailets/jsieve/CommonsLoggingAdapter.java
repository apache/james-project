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

import java.util.Optional;

import org.apache.commons.logging.Log;
import org.slf4j.Logger;

import com.google.common.base.Preconditions;

/**
 * Adapts commons logging to mailet logging.
 */
public class CommonsLoggingAdapter implements Log {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<Boolean> verbose = Optional.empty();
        private Optional<Boolean> quiet = Optional.empty();
        private Logger logger;

        public Builder wrappedLogger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = Optional.of(verbose);
            return this;
        }

        public Builder quiet(boolean quiet) {
            this.quiet = Optional.of(quiet);
            return this;
        }

        public CommonsLoggingAdapter build() {
            Preconditions.checkNotNull(logger);
            Boolean quietParameter = quiet.orElse(false);
            Boolean verboseParameter = verbose.orElse(false);
            Preconditions.checkState(!(verboseParameter && quietParameter), "You can not specify a logger both verbose and quiet");
            return new CommonsLoggingAdapter(logger, computeLogLevel(quietParameter, verboseParameter));
        }

        private int computeLogLevel(boolean quiet, boolean verbose) {
            if (verbose) {
                return CommonsLoggingAdapter.TRACE;
            } else if (quiet) {
                return CommonsLoggingAdapter.FATAL;
            } else {
                return CommonsLoggingAdapter.WARN;
            }
        }
    }

    public static final int TRACE = 6;
    public static final int DEBUG = 5;
    public static final int INFO = 4;
    public static final int WARN = 3;
    public static final int ERROR = 2;
    public static final int FATAL = 1;
    
    private final Logger logger;
    private final int level;
    
    private CommonsLoggingAdapter(Logger logger, final int level) {
        super();
        this.logger = logger;
        this.level = level;
    }

    public void debug(Object message) {
        if (isDebugEnabled()) {
            logger.debug("{}", (message == null ? "NULL" : message));
        }
    }

    public void debug(Object message, Throwable t) {
        if (isDebugEnabled()) {
            logger.debug("{}", (message == null ? "NULL" : message), t);
        } 
    }

    public void error(Object message) {
        if (isErrorEnabled()) {
            logger.error("{}", (message == null ? "NULL" : message));
        }
    }

    public void error(Object message, Throwable t) {
        if (isErrorEnabled()) {
            logger.error("{}", (message == null ? "NULL" : message), t);
        }
    }

    public void fatal(Object message) {
        if (isFatalEnabled()) {
            logger.error("{}", (message == null ? "NULL" : message));
        }
    }

    public void fatal(Object message, Throwable t) {
        if (isFatalEnabled()) {
            logger.error("{}", (message == null ? "NULL" : message), t);
        }
    }

    public void info(Object message) {
        if (isInfoEnabled()) {
            logger.info("{}", (message == null ? "NULL" : message));
        }
    }

    public void info(Object message, Throwable t) {
        if (isInfoEnabled()) {
            logger.info("{}", (message == null ? "NULL" : message), t);
        }
    }

    public boolean isDebugEnabled() {
        return level >= DEBUG;
    }

    public boolean isErrorEnabled() {
        return level >= ERROR;
    }

    public boolean isFatalEnabled() {
        return level >= FATAL;
    }

    public boolean isInfoEnabled() {
        return level >= INFO;
    }

    public boolean isTraceEnabled() {
        return level >= TRACE;
    }

    public boolean isWarnEnabled() {
        return level >= WARN;
    }

    public void trace(Object message) {
        if (isTraceEnabled()) {
            logger.debug("{}", (message == null ? "NULL" : message));
        }
    }

    public void trace(Object message, Throwable t) {
        if (isTraceEnabled()) {
            logger.debug("{}", (message == null ? "NULL" : message), t);
        }
    }

    public void warn(Object message) {
        if (isWarnEnabled()) {
            logger.warn("{}", (message == null ? "NULL" : message));
        }
    }

    public void warn(Object message, Throwable t) {
        if (isWarnEnabled()) {
            logger.warn("{}", (message == null ? "NULL" : message), t);
        }
    }

}
