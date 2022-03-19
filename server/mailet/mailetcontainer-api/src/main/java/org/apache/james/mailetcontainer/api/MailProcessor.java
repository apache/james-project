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

package org.apache.james.mailetcontainer.api;

import jakarta.mail.MessagingException;

import org.apache.mailet.Mail;

/**
 * The <code>service</code> perform all needed work on the Mail object. Whatever
 * remains at the end of the service is considered to need futher processing and
 * will go to the next Mailet if there is one configured or will go to the error
 * processor if not. Setting a Mail state (setState(String)) to Mail.GHOST or
 * cleaning its recipient list has the same meaning that s no more processing is
 * needed.
 */
public interface MailProcessor {
    /**
     * <p>
     * Called by the mailet container to allow the mailet to process to a
     * message.
     * </p>
     * <p>
     * This method is only called after the mailet's init() method has completed
     * successfully.
     * </p>
     * <p>
     * Mailets typically run inside multithreaded mailet containers that can
     * handle multiple requests concurrently. Developers must be aware to
     * synchronize access to any shared resources such as files, network
     * connections, as well as the mailet's class and instance variables. More
     * information on multithreaded programming in Java is available in <a href=
     * "http://java.sun.com/Series/Tutorial/java/threads/multithreaded.html">the
     * Java tutorial on multi-threaded programming</a>.
     * </p>
     * 
     * @param mail
     *            - the Mail object that contains the message and routing
     *            information
     * @throws MessagingException
     *             - if a message or address parsing exception occurs or an
     *             exception that interferes with the mailet's normal operation
     */
    void service(Mail mail) throws MessagingException;

}
