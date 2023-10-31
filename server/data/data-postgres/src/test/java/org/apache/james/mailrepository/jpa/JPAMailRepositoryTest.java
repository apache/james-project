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

package org.apache.james.mailrepository.jpa;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.mailrepository.MailRepositoryContract;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.mailrepository.jpa.model.JPAMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

public class JPAMailRepositoryTest implements MailRepositoryContract {

    final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPAMail.class);

    private JPAMailRepository mailRepository;

    @BeforeEach
    void setUp() throws Exception {
        mailRepository = retrieveRepository(MailRepositoryPath.from("testrepo"));
    }

    @AfterEach
    void tearDown() {
        JPA_TEST_CLUSTER.clear("JAMES_MAIL_STORE");
    }

    @Override
    public MailRepository retrieveRepository() {
        return mailRepository;
    }

    @Override
    public JPAMailRepository retrieveRepository(MailRepositoryPath url) throws Exception {
        BaseHierarchicalConfiguration conf = new BaseHierarchicalConfiguration();
        conf.addProperty("[@destinationURL]", MailRepositoryUrl.fromPathAndProtocol(new Protocol("jpa"), url).asString());
        JPAMailRepository mailRepository = new JPAMailRepository(JPA_TEST_CLUSTER.getEntityManagerFactory());
        mailRepository.configure(conf);
        mailRepository.init();
        return mailRepository;
    }

    @Override
    @Disabled("JAMES-3431 No support for Attribute collection Java serialization yet")
    public void shouldPreserveDsnParameters() throws Exception {
        MailRepositoryContract.super.shouldPreserveDsnParameters();
    }
}
