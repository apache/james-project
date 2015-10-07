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
package org.apache.james.container.spring.tool;

import org.apache.james.mailbox.MailboxManager;

/**
 * Allow to copy {@link MailboxManager} contents from one to the other via JMX
 */
public interface James23ImporterManagementMBean {
    
    /**
     * First import users (each user is created with the given default  password), the
     * import the mails for each users from the given mail repository path.
     * 
     * @param james23MailRepositoryPath
     * @param defaultPassword
     */
    void importUsersAndMailsFromJames23(String james23MailRepositoryPath, String defaultPassword) throws Exception;

    /**
     * Import users (each user is created with the given default  password).
     * 
     * @param defaultPassword
     * @throws Exception
     */
    void importUsersFromJames23(String defaultPassword) throws Exception;
    
    /**
     * Import the mails for each users from the given mail repository path.
     * 
     * @param james23MailRepositoryPath
     * @throws Exception
     */
    void importMailsFromJames23(String james23MailRepositoryPath) throws Exception;
    
}
