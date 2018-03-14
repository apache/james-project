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

package org.apache.james.user.lib.model;

import org.apache.james.core.MailAddress;
import org.apache.james.user.api.model.JamesUser;

/**
 * Implementation of User Interface.
 * 
 * @deprecated Use {@link DefaultUser}
 */
@Deprecated
public class DefaultJamesUser extends DefaultUser implements JamesUser {

    private static final long serialVersionUID = 6323959976390389529L;

    /**
     * Whether forwarding is enabled for this user.
     */
    private boolean forwarding;

    /**
     * The mail address to which this user's email is forwarded.
     */
    private MailAddress forwardingDestination;

    /**
     * Is this user an alias for another username on the system.
     */
    private boolean aliasing;

    /**
     * The user name that this user name is aliasing.
     */
    private String alias;

    public DefaultJamesUser(String name, String alg) {
        super(name, alg);
        initialize();
    }

    public DefaultJamesUser(String name, String passwordHash, String hashAlg) {
        super(name, passwordHash, hashAlg);
        initialize();
    }

    /**
     * Initializes default values for local fields.
     */
    public void initialize() {
        forwarding = false;
        forwardingDestination = null;
        aliasing = false;
        alias = "";
    }

    @Override
    public void setForwarding(boolean forward) {
        forwarding = forward;
    }

    @Override
    public boolean getForwarding() {
        return forwarding;
    }

    @Override
    public boolean setForwardingDestination(MailAddress address) {
        /* TODO: Some verification would be good */
        forwardingDestination = address;
        return true;
    }

    @Override
    public MailAddress getForwardingDestination() {
        return forwardingDestination;
    }

    @Override
    public void setAliasing(boolean alias) {
        aliasing = alias;
    }

    @Override
    public boolean getAliasing() {
        return aliasing;
    }

    @Override
    public boolean setAlias(String address) {
        /* TODO: Some verification would be good */
        alias = address;
        return true;
    }

    @Override
    public String getAlias() {
        return alias;
    }
}
