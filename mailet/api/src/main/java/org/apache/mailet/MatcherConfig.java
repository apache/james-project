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


package org.apache.mailet;

/**
 * A matcher configuration object used by the mailet container to pass
 * information to a matcher during initialization.
 * <p>
 * The configuration information consists of the matcher's condition string,
 * and a MailetContext object which allows the matcher to interact with the
 * mailet container.
 */
public interface MatcherConfig {

    /**
     * The condition defined for this matcher.
     * <p>
     * For example, the SenderIs matcher might be configured as
     * "SenderIs=admin@localhost", in which case the condition would be
     * "admin@localhost".
     *
     * @return a String containing the value of the initialization parameter
     */
    String getCondition();

    /**
     * Returns a reference to the MailetContext in which the matcher is executing
     *
     * @return a MailetContext object which can be used by the matcher
     *      to interact with the mailet container
     */
    MailetContext getMailetContext();

    /**
     * Returns the name of this matcher instance. The name may be provided via server
     * administration, assigned in the application deployment descriptor, or, for
     * an unregistered (and thus unnamed) matcher instance, it will be the matcher's
     * class name.
     *
     * @return the name of the matcher instance
     */
    String getMatcherName();
}
