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

import java.io.InputStream;
import java.io.InputStreamReader;

import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.commons.cnd.CndImporter;

/**
 * Utilities used for JCR 
 *
 */
public class JCRUtils implements JCRImapConstants {

    /**
     * Register the imap CND file in the workspace
     * 
     * @param repository
     * @param workspace
     * @param username
     * @param password
     */
    public static void registerCnd(Repository repository, String workspace, String username, String password) {
        try {
            Session session;
            if (username == null) {
                session = repository.login(workspace);
            } else {
                char[] pass;
                if (password == null) {
                    pass = new char[0];
                } else {
                    pass = password.toCharArray();
                }
                session = repository.login(new SimpleCredentials(username, pass), workspace);
            }
            registerCnd(session);
            session.logout();
        } catch (Exception e) {
            throw new RuntimeException("Unable to register cnd file", e);
        }    
    }
    
    /**
     * Register the imap CND file 
     */
    public static void registerCnd(Session session) {
        // Register the custom node types defined in the CND file
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        try (InputStream is = contextClassLoader.getResourceAsStream("mailbox-jcr.cnd")) {
            CndImporter.registerNodeTypes(new InputStreamReader(is), session);
        } catch (Exception e) {
            throw new RuntimeException("Unable to register cnd file", e);
        }
    }
    
}
