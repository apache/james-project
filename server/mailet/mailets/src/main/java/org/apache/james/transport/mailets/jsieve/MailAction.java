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

import jakarta.mail.MessagingException;

import org.apache.jsieve.mail.Action;
import org.apache.mailet.Mail;

/**
 * Executes a Sieve action.
 * Implementations may be accessed concurrently by multiple threads.
 */
public interface MailAction {
    
    /**
     * Executes the given action.
     * @param action not null
     * @param mail not null
     * @param context not null
     * @throws MessagingException when action cannot be executed
     */
    public void execute(final Action action, final Mail mail, final ActionContext context) throws MessagingException;
}
