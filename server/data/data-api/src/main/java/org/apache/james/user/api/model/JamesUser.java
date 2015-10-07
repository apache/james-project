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

package org.apache.james.user.api.model;

import org.apache.mailet.MailAddress;

/**
 * Interface for objects representing users of an email/ messaging system.
 * 
 * @deprecated Just use the User interface for Users. Forwarding and Aliasing
 *             should get done via RecipientRewriteTable
 */
@Deprecated
public interface JamesUser extends User {

    /**
     * Indicate if mail for this user should be forwarded to some other mail
     * server.
     * 
     * @param forward
     *            whether email for this user should be forwarded
     */
    void setForwarding(boolean forward);

    /**
     * Return true if mail for this user should be forwarded
     */
    boolean getForwarding();

    /**
     * <p>
     * Set destination for forwading mail
     * </p>
     * <p>
     * TODO: Should we use a MailAddress?
     * </p>
     * 
     * @param address
     *            the forwarding address for this user
     */
    boolean setForwardingDestination(MailAddress address);

    /**
     * Return the destination to which email should be forwarded
     */
    MailAddress getForwardingDestination();

    /**
     * Indicate if mail received for this user should be delivered locally to a
     * different address.
     */
    void setAliasing(boolean alias);

    /**
     * Return true if emails should be delivered locally to an alias.
     */
    boolean getAliasing();

    /**
     * Set local address to which email should be delivered.
     * 
     * @return true if successful
     */
    boolean setAlias(String address);

    /**
     * Get local address to which mail should be delivered.
     */
    String getAlias();

}
