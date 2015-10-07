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
package org.apache.james.mailbox.jcr;

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.james.mailbox.MailboxSession;


/**
 * 
 * Manage JCR {@link Session}. It will use one user and password for all 
 *
 */
public class GlobalMailboxSessionJCRRepository extends MailboxSessionJCRRepository{

    private String username;
    private char[] pass;

    public GlobalMailboxSessionJCRRepository(Repository repository, String workspace, String username, String password) {
        super(repository, workspace);
        this.username = username;
        if (password == null) {
            pass = null;
        } else {
            pass = password.toCharArray();
        }
    }

    /**
     * Login in the {@link Repository} with the global configured user and password
     */
    @Override
    public Session login(MailboxSession session) throws RepositoryException {
        return login(session, username, pass);
    }

}
