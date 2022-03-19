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

import org.apache.mailet.Mail;

/**
 * Experimental interface.
 */
public interface Poster {
    
    /**
     * Experimental mail delivery. 
     * POST verb indicate that mail should be attached to the collection
     * indicated by the given URI.
     * 
     * @param uri indicates the destination to which the mail to added. ATM 
     * the value should be mailbox://<user>@localhost/<mailbox-path>
     * @param mail not null
     */
    void post(String uri, Mail mail) throws MessagingException;
}
