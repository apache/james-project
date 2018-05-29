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
package org.apache.james.user.jcr;

import java.io.File;
import java.io.IOException;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.james.user.lib.AbstractUsersRepository;
import org.apache.james.user.lib.AbstractUsersRepositoryTest;
import org.junit.After;
import org.junit.Before;
import org.xml.sax.InputSource;

public class JcrUserRepositoryTest extends AbstractUsersRepositoryTest {

    private static final String JACKRABBIT_HOME = "target/jackrabbit";
    private RepositoryImpl repository;

    @Override
    protected AbstractUsersRepository getUsersRepository() throws Exception {
        JCRUsersRepository repos = new JCRUsersRepository();
        repos.setRepository(repository);
        DefaultConfigurationBuilder config = new DefaultConfigurationBuilder();
        config.addProperty("username", "admin");
        config.addProperty("password", "test");
        repos.configure(config);
        return repos;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        File home = new File(JACKRABBIT_HOME);
        if (home.exists()) {
            delete(home);
        }
        RepositoryConfig config = RepositoryConfig.create(new InputSource(this.getClass().getClassLoader()
                .getResourceAsStream("test-repository.xml")), JACKRABBIT_HOME);
        repository = RepositoryImpl.create(config);
        super.setUp();
    }

    private void delete(File file) {
        try {
            FileUtils.forceDelete(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        repository.shutdown();
    }
}
