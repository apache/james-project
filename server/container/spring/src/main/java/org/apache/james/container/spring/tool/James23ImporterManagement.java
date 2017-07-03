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

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link James23Importer} support via JMX.
 */
public class James23ImporterManagement implements James23ImporterManagementMBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(James23ImporterManagement.class);

    @Inject
    private James23Importer james23Importer;

    @Override
    public void importUsersAndMailsFromJames23(String james23MailRepositoryPath, String defaultPassword) throws Exception {
        try {
            james23Importer.importUsersAndMailsFromJames23(james23MailRepositoryPath, defaultPassword);
        } catch (Exception e) {
            throw new Exception("Error while importing users and mails", e);
        }
    }

    @Override
    public void importUsersFromJames23(String defaultPassword) throws Exception {
        try {
            james23Importer.importUsersFromJames23(defaultPassword);
        } catch (Exception e) {
            throw new Exception("Error while importing users", e);
        }
    }

    @Override
    public void importMailsFromJames23(String james23MailRepositoryPath) throws Exception {
        try {
            james23Importer.importMailsFromJames23(james23MailRepositoryPath);
        } catch (Exception e) {
            LOGGER.error("Error while importing mail", e);
            throw new Exception(e.getMessage());
        }
    }

}
