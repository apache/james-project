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

package org.apache.james.user.jdbc;

import java.util.Iterator;

import javax.sql.DataSource;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.derby.jdbc.EmbeddedDriver;
import org.apache.james.filesystem.api.mock.MockFileSystem;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.lib.AbstractUsersRepository;

/**
 * Test basic behaviors of UsersFileRepository
 */
public class JamesUsersJdbcRepositoryTest extends AbstractUsersJdbcRepositoryTest {

    /**
     * Create the repository to be tested.
     *
     * @return the user repository
     * @throws Exception
     */
    @Override
    protected AbstractUsersRepository getUsersRepository() throws Exception {
        @SuppressWarnings("deprecation")
        JamesUsersJdbcRepository res = new JamesUsersJdbcRepository();
        String tableString = "jamesusers";
        configureAbstractJdbcUsersRepository(res, tableString);
        return res;
    }

    @SuppressWarnings("deprecation")
    protected void configureAbstractJdbcUsersRepository(AbstractJdbcUsersRepository res, String tableString) throws Exception {
        res.setFileSystem(new MockFileSystem());
        DataSource dataSource = getDataSource();
        res.setDatasource(dataSource);
        DefaultConfigurationBuilder configuration = new DefaultConfigurationBuilder();
        configuration.addProperty("[@destinationURL]", "db://maildb/" + tableString);
        configuration.addProperty("sqlFile", "file://conf/sqlResources.xml");
        res.configure(configuration);
        res.init();
    }

    private BasicDataSource getDataSource() {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName(EmbeddedDriver.class.getName());
        ds.setUrl("jdbc:derby:memory:testdb;create=true");
        ds.setUsername("james");
        ds.setPassword("james");
        return ds;
    }

    @Override
    protected void disposeUsersRepository() throws UsersRepositoryException {
        Iterator<String> i = this.usersRepository.list();
        while (i.hasNext()) {
            this.usersRepository.removeUser(i.next());
        }
        LifecycleUtil.dispose(this.usersRepository);
    }

}
