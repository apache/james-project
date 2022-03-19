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

import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;

/**
 * Implementations of this interface are responsible to create {@link Mailet}
 * instances
 */
public interface MailetLoader {

    /**
     * Get a new {@link Mailet} instance for the given {@link MailetConfig}. The
     * returned {@link Mailet} needs to be fully initialized, so the returned
     * instance is "read-to-use"
     *
     * @throws MessagingException
     *             if an error occurs
     */
    Mailet getMailet(MailetConfig config) throws MessagingException;

}
