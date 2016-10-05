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

import org.joda.time.DateTime;

import java.io.InputStream;

/**
 * <p>Experimental API locates resources. 
 * Used to load Sieve scripts. The base for relative URLs
 * should be taken to be the root of the James configuration.
 * </p><p>
 * Required schemas:
 * </p>
 * <ul>
 * <li><strong>User sieve scripts</strong> - the relative URL scheme 
 * <code>//<em>user</em>@<em>host</em>/<em>sieve</em> will be used to
 * obtain the script
 * </ul>
 * <p>
 * The advantage of using <code>URI</code>s 
 * and verbs (for example <code>GET</code>, <code>POST</code>)
 * are their uniformity. The same API can be used to interface radically
 * different resource types and protocols. This allows concise, minimal,
 * powerful APIs to be created. Their simplicity is easy to preserved 
 * across versions. 
 * </p><p>
 * The disadvantage is that this free decouple means that there is 
 * no gaurantee that the implementations decoupled by this interface
 * actually support the same scheme. Issues will be caught only 
 * at deployment and not at compile time.
 * This places a larger burden on the deployer.
 * </p><p>
 * Either an understanding or a consistent URL mapping scheme may be 
 * required. For example, <code>//john.smith@localhost/sieve</code>
 * may need to be resolved to <code>../apps/james/var/sieve/john.smith@localhost.sieve</code>
 * when using the file system to store scripts. Note that names <strong>MUST</strong>
 * be normalised before resolving on a file system.
 * </p>
 */
public interface ResourceLocator {

    class UserSieveInformation {
        private DateTime scriptActivationDate;
        private DateTime scriptInterpretationDate;
        private InputStream scriptContent;

        public UserSieveInformation(DateTime scriptActivationDate, DateTime scriptInterpretationDate, InputStream scriptContent) {
            this.scriptActivationDate = scriptActivationDate;
            this.scriptInterpretationDate = scriptInterpretationDate;
            this.scriptContent = scriptContent;
        }

        public DateTime getScriptActivationDate() {
            return scriptActivationDate;
        }

        public DateTime getScriptInterpretationDate() {
            return scriptInterpretationDate;
        }

        public InputStream getScriptContent() {
            return scriptContent;
        }
    }

    /**
     * GET verb locates and loads a resource. 
     * @param uri identifies the Sieve script 
     * @return not null
     * @throws Exception when the resource cannot be located
     */
    UserSieveInformation get(String uri) throws Exception;

}
