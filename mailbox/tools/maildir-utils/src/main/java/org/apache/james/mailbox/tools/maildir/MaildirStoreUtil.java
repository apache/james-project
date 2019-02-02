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
package org.apache.james.mailbox.tools.maildir;

import java.io.FileNotFoundException;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.maildir.MaildirStore;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;

/**
 * Utility to instance a {@link MaildirStore} object
 */
public class MaildirStoreUtil {

    /**
     * Return a {@link MaildirStore} instance
     * 
     * @param fs
     * @param rootURL
     * @param locker
     * @return store
     * @throws FileNotFoundException
     * @throws UsersRepositoryException
     */
    public static MaildirStore create(FileSystem fs, UsersRepository usersRepos, String rootURL, MailboxPathLocker locker) throws FileNotFoundException, UsersRepositoryException {
        StringBuilder root = new StringBuilder();
        root.append(fs.getFile(rootURL).getAbsolutePath());
        if (usersRepos.supportVirtualHosting()) {
            root.append("/").append(MaildirStore.PATH_DOMAIN).append("/").append(MaildirStore.PATH_USER).append("/");
        } else {
            root.append("/").append(MaildirStore.PATH_USER).append("/");
        }

        return new MaildirStore(root.toString(), locker);
    }
}
