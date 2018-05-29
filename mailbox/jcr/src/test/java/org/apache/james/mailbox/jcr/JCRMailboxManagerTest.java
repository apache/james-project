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

import static org.apache.james.mailbox.jcr.JCRMailboxManagerProvider.JACKRABBIT_HOME;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManagerTest;
import org.junit.After;
import org.junit.Before;

public class JCRMailboxManagerTest extends MailboxManagerTest {

    private Optional<RepositoryImpl> repository = Optional.empty();

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }
    
    @Override
    protected MailboxManager provideMailboxManager() {
        String user = "user";
        String pass = "pass";
        String workspace = null;

        if (!repository.isPresent()) {
            repository = Optional.of(JCRMailboxManagerProvider.createRepository());
        }

        return JCRMailboxManagerProvider.provideMailboxManager(user, pass, workspace, repository.get());
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        if (repository.isPresent()) {
            repository.get().shutdown();
            try {
                FileUtils.forceDelete(new File(JACKRABBIT_HOME));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
