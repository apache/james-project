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

import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.mailrepository.api.MailRepositoryStore.MailRepositoryStoreException;
import org.apache.james.user.api.UsersRepositoryException;

import javax.inject.Inject;
import javax.mail.MessagingException;
import java.io.IOException;

/**
 * {@link James23Importer} support via JMX.
 */
public class James23ImporterManagement implements James23ImporterManagementMBean {

    @Inject
    private James23Importer james23Importer;

    @Override
    public void importUsersAndMailsFromJames23(String james23MailRepositoryPath, String defaultPassword) throws Exception {
        try {
            james23Importer.importUsersAndMailsFromJames23(james23MailRepositoryPath, defaultPassword);
        } catch (MailRepositoryStoreException e) {
            throw new Exception(e.getMessage());
        } catch (MessagingException e) {
            throw new Exception(e.getMessage());
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());
        } catch (DomainListException e) {
            throw new Exception(e.getMessage());
        } catch (IOException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public void importUsersFromJames23(String defaultPassword) throws Exception {
        try {
            james23Importer.importUsersFromJames23(defaultPassword);
        } catch (MessagingException e) {
            throw new Exception(e.getMessage());
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());
        } catch (DomainListException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public void importMailsFromJames23(String james23MailRepositoryPath) throws Exception {
        try {
            james23Importer.importMailsFromJames23(james23MailRepositoryPath);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception(e.getMessage());
            // } catch (MailboxException e) {
            // e.printStackTrace();
            // throw new Exception(e.getMessage());
            // } catch (MailRepositoryStoreException e) {
            // e.printStackTrace();
            // throw new Exception(e.getMessage());
            // } catch (MessagingException e) {
            // e.printStackTrace();
            // throw new Exception(e.getMessage());
            // } catch (UsersRepositoryException e) {
            // e.printStackTrace();
            // throw new Exception(e.getMessage());
            // } catch (IOException e) {
            // e.printStackTrace();
            // throw new Exception(e.getMessage());
        }
    }

}
