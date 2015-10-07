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
import javax.jcr.SimpleCredentials;

import org.apache.james.mailbox.store.Authenticator;

/**
 * {@link Authenticator} implementation which will try to log in the {@link Repository} and obtain a Session. If this fails it will handle the 
 * user/pass as invalid. If it success it will logout from the session again and handle the user/pass as valid.
 * 
 * This implementation should be used if you want to handle the IMAP Users transparent to the JCR Users. The use of the implementation only makes
 * real sense in conjunction with the direct {@link MailboxSessionJCRRepository} implementation (not a subclass).
 *
 */
public class JCRRepositoryAuthenticator implements Authenticator{

    private MailboxSessionJCRRepository repository;
    
    public JCRRepositoryAuthenticator(MailboxSessionJCRRepository repository) {
        this.repository = repository;
    }
    
    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.Authenticator#isAuthentic(java.lang.String, java.lang.CharSequence)
     */
    public boolean isAuthentic(String userid, CharSequence passwd) {
        Repository repos = repository.getRepository();
        try {
            // Try to log in the Repository with the user and password. If this success we can asume that the user/pass is valid. In 
            // all cases we need to logout again to not bind two JCR Session per thread later
            Session session = repos.login(new SimpleCredentials(userid,passwd.toString().toCharArray()), repository.getWorkspace());
            session.logout();
            return true;
        } catch (RepositoryException e) {
            return false;
        }
    }

}
