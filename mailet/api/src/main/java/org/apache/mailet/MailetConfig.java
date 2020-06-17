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

import java.util.Iterator;

/**
 * A mailet configuration object used by the mailet container to pass
 * information to a mailet during initialization.
 * <p>
 * The configuration information consists of mailet-specific initialization
 * parameters in a set of name/value pairs, and a MailetContext object
 * which allows the mailet to interact with the mailet container.
 */
public interface MailetConfig {

    /**
     * Returns the value of the named initialization parameter,
     * or null if the parameter does not exist.
     * Multi-valued parameters are returned as a comma-delineated string.
     *
     * @param name the name of the initialization parameter
     * @return the value of the initialization parameter, or null
     */
    String getInitParameter(String name);

    /**
     * Returns the names of the mailet's initialization parameters as an
     * Iterator of String objects, or an empty Iterator if the mailet has
     * no initialization parameters.
     *
     * @return an Iterator of String objects containing the names of the
     *      mailet's initialization parameters
     */
    Iterator<String> getInitParameterNames();

    /**
     * Returns a reference to the MailetContext in which the mailet is
     * executing.
     *
     * @return a MailetContext object which can be used by the mailet
     *      to interact with the mailet container
     */
    MailetContext getMailetContext();

    /**
     * Returns the name of this mailet instance. The name may be provided via
     * server administration, assigned in the application deployment descriptor,
     * or, for an unregistered (and thus unnamed) mailet instance, it will be
     * the mailet's class name.
     *
     * @return the name of the mailet instance
     */
    String getMailetName();
}
