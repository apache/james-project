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

public interface Logger {

    /**
     * <p> Is debug logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than debug. </p>
     *
     * @return true if debug is enabled in the underlying logger.
     */
    boolean isDebugEnabled();


    /**
     * <p> Is error logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than error. </p>
     *
     * @return true if error is enabled in the underlying logger.
     */
    boolean isErrorEnabled();

    /**
     * <p> Is info logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than info. </p>
     *
     * @return true if info is enabled in the underlying logger.
     */
    boolean isInfoEnabled();


    /**
     * <p> Is trace logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than trace. </p>
     *
     * @return true if trace is enabled in the underlying logger.
     */
    boolean isTraceEnabled();


    /**
     * <p> Is warn logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than warn. </p>
     *
     * @return true if warn is enabled in the underlying logger.
     */
    boolean isWarnEnabled();


    /**
     * <p> Log a message with trace log level. </p>
     *
     * @param message log this message
     */
    void trace(String message);


    /**
     * <p> Log an error with trace log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    void trace(String message, Throwable t);


    /**
     * <p> Log a message with debug log level. </p>
     *
     * @param message log this message
     */
    void debug(String message);


    /**
     * <p> Log an error with debug log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    void debug(String message, Throwable t);


    /**
     * <p> Log a message with info log level. </p>
     *
     * @param message log this message
     */
    void info(String message);


    /**
     * <p> Log an error with info log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    void info(String message, Throwable t);


    /**
     * <p> Log a message with warn log level. </p>
     *
     * @param message log this message
     */
    public void warn(String message);


    /**
     * <p> Log an error with warn log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    void warn(String message, Throwable t);


    /**
     * <p> Log a message with error log level. </p>
     *
     * @param message log this message
     */
    void error(String message);


    /**
     * <p> Log an error with error log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    void error(String message, Throwable t);
}
