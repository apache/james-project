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
package org.apache.james.smtpserver.netty;

import org.apache.james.protocols.lib.jmx.ServerMBean;

/**
 * JMX MBean interface for the SMTPServer
 */
public interface SMTPServerMBean extends org.apache.james.protocols.smtp.SMTPServerMBean, ServerMBean {

    /**
     * Return the maximum allowed size of the message
     */
    long getMaximalMessageSize();

    /**
     * Set the maximum allowed size of messages. Set this to 0 to accept every
     * message
     */
    void setMaximalMessageSize(long maxSize);

    /**
     * Return true if brackets around addresses in the MAIL and RCPT are
     * required
     * 
     * @return bracketsEnforcement
     */
    boolean getAddressBracketsEnforcement();

    /**
     * Enable or disable brackets enforcement around addressed in the MAIL and
     * RCPT command
     */
    void setAddressBracketsEnforcement(boolean enforceAddressBrackets);

    /**
     * Return true if a HELO/EHLO is required when connecting to this server
     * 
     * @return heloEhloEnforcement
     */
    boolean getHeloEhloEnforcement();

    /**
     * Enable or disable the need of the HELO/EHLO
     */
    void setHeloEhloEnforcement(boolean enforceHeloEHlo);

    /**
     * Return the hello name
     */
    String getHeloName();
}
