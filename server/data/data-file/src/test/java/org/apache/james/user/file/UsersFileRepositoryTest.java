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

package org.apache.james.user.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.lib.AbstractUsersRepository;
import org.apache.james.user.lib.AbstractUsersRepositoryTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test basic behaviors of UsersFileRepository
 */
public class UsersFileRepositoryTest extends AbstractUsersRepositoryTest {
    
    private static final String TARGET_REPOSITORY_FOLDER = "target/var/users";
	private File targetRepositoryFolder;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        targetRepositoryFolder = new File(TARGET_REPOSITORY_FOLDER);
        this.usersRepository = getUsersRepository();
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.forceDelete(targetRepositoryFolder);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected AbstractUsersRepository getUsersRepository() throws Exception {
        FileSystem fs = new FileSystem() {

            @Override
            public File getBasedir() throws FileNotFoundException {
                return new File(".");
            }

            @Override
            public InputStream getResource(String url) throws IOException {
                return new FileInputStream(getFile(url));
            }

            @Override
            public File getFile(String fileURL) throws FileNotFoundException {
                return new File(fileURL.substring(FileSystem.FILE_PROTOCOL.length()));
            }

        };

        DefaultConfigurationBuilder configuration = new DefaultConfigurationBuilder("test");
        configuration.addProperty("destination.[@URL]", "file://target/var/users");
        // Configure with ignoreCase = false, we need some more work to support true
        configuration.addProperty("ignoreCase", "false");

        UsersFileRepository res = new UsersFileRepository();

        res.setFileSystem(fs);
        res.configure(configuration);
        res.init();
        return res;
    }
    
    @Override
    @Ignore
    @Test
    public void addUserShouldThrowWhenSameUsernameWithDifferentCase() throws UsersRepositoryException {
    }

    protected void disposeUsersRepository() throws UsersRepositoryException {
        if (this.usersRepository != null) {
            Iterator<String> i = this.usersRepository.list();
            while (i.hasNext()) {
                this.usersRepository.removeUser(i.next());
            }
            LifecycleUtil.dispose(this.usersRepository);
        }
    }
}
