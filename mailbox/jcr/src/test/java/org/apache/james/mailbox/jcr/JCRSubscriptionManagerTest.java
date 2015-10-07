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

import java.io.File;
import java.io.IOException;

import javax.jcr.RepositoryException;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.james.mailbox.AbstractSubscriptionManagerTest;
import org.apache.james.mailbox.SubscriptionManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.xml.sax.InputSource;

public class JCRSubscriptionManagerTest extends AbstractSubscriptionManagerTest {
    private static final String JACKRABBIT_HOME = "target/jackrabbit";

    public static final String META_DATA_DIRECTORY = "target/user-meta-data";

    private static RepositoryImpl repository;
    private static String user = "user";
    private static String pass = "pass";
    private static String workspace = null;

    @BeforeClass
    public static void before() throws RepositoryException {
        RepositoryConfig config = RepositoryConfig.create(new InputSource(JCRMailboxManagerTest.class.getClassLoader().getResourceAsStream("test-repository.xml")), JACKRABBIT_HOME);
        repository = RepositoryImpl.create(config);

        // Register imap cnd file
        JCRUtils.registerCnd(repository, workspace, user, pass);
    }

    @AfterClass
    public static void after() throws IOException {
        if (repository != null) {
            repository.shutdown();
        }
        FileUtils.forceDelete(new File(JACKRABBIT_HOME));
    }

    @Override
    public SubscriptionManager createSubscriptionManager() {
        MailboxSessionJCRRepository sessionRepos = new GlobalMailboxSessionJCRRepository(repository, workspace, user, pass);
        JCRMailboxSessionMapperFactory mf = new JCRMailboxSessionMapperFactory(sessionRepos, null, null);
        JCRSubscriptionManager sm = new JCRSubscriptionManager(mf);
        return sm;
    }

}
