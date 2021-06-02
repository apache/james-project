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

package org.apache.james.mailetcontainer.impl;

import org.apache.mailet.MailetContext;
import org.apache.mailet.MatcherConfig;

/**
 * Implements the configuration object for a Matcher.
 */
public class MatcherConfigImpl implements MatcherConfig {

    /** A String representation of the value for the matching condition */
    private String condition;

    /** The name of the Matcher */
    private String name;

    /** The MailetContext associated with the Matcher configuration */
    private MailetContext context;

    /**
     * The simple condition defined for this matcher, e.g., for
     * SenderIs=admin@localhost, this would return admin@localhost.
     * 
     * @return a String containing the value of the initialization parameter
     */
    @Override
    public String getCondition() {
        return condition;
    }

    /**
     * Set the simple condition defined for this matcher configuration.
     */
    public void setCondition(String newCondition) {
        condition = newCondition;
    }

    /**
     * Returns the name of this matcher instance. The name may be provided via
     * server administration, assigned in the application deployment descriptor,
     * or for an unregistered (and thus unnamed) matcher instance it will be the
     * matcher's class name.
     * 
     * @return the name of the matcher instance
     */
    @Override
    public String getMatcherName() {
        return name;
    }

    /**
     * Sets the name of this matcher instance.
     * 
     * @param newName
     *            the name of the matcher instance
     */
    public void setMatcherName(String newName) {
        name = newName;
    }

    /**
     * Returns a reference to the MailetContext in which the matcher is
     * executing
     * 
     * @return a MailetContext object, used by the matcher to interact with its
     *         mailet container
     */
    @Override
    public MailetContext getMailetContext() {
        return context;
    }

    /**
     * Sets a reference to the MailetContext in which the matcher is executing
     * 
     * @param newContext
     *            a MailetContext object, used by the matcher to interact with
     *            its mailet container
     */
    public void setMailetContext(MailetContext newContext) {
        context = newContext;
    }
}
