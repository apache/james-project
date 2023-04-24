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

package org.apache.james.mailrepository.cassandra;

import javax.inject.Inject;

import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryFactory;
import org.apache.james.mailrepository.api.MailRepositoryUrl;

public class CassandraMailRepositoryFactory implements MailRepositoryFactory {
    private final CassandraMailRepositoryKeysDAO keysDAO;
    private final CassandraMailRepositoryMailDaoV2 mailDAO;
    private final MimeMessageStore.Factory mimeMessageStoreFactory;

    @Inject
    public CassandraMailRepositoryFactory(
            CassandraMailRepositoryKeysDAO keysDAO,
            CassandraMailRepositoryMailDaoV2 mailDAO,
            MimeMessageStore.Factory mimeMessageStoreFactory) {
        this.keysDAO = keysDAO;
        this.mailDAO = mailDAO;
        this.mimeMessageStoreFactory = mimeMessageStoreFactory;
    }

    @Override
    public Class<? extends MailRepository> mailRepositoryClass() {
        return CassandraMailRepository.class;
    }

    @Override
    public MailRepository create(MailRepositoryUrl url) {
        return new CassandraMailRepository(url, keysDAO, mailDAO, mimeMessageStoreFactory);
    }
}
