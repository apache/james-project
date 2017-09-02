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

package org.apache.james.mailrepository;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.filesystem.api.mock.MockFileSystem;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.file.FileMailRepository;

public class FileMailRepositoryTest extends AbstractMailRepositoryTest {

    /**
     * @return
     * @throws ServiceException
     * @throws ConfigurationException
     * @throws Exception
     */
    @Override
    protected MailRepository getMailRepository() throws Exception {
        MockFileSystem fs = new MockFileSystem();
        FileMailRepository mr = new FileMailRepository();
        mr.setFileSystem(fs);
        DefaultConfigurationBuilder defaultConfiguration = new DefaultConfigurationBuilder();
        defaultConfiguration.addProperty("[@destinationURL]", "file://target/var/mr");
        defaultConfiguration.addProperty("[@type]", "MAIL");
        mr.configure(defaultConfiguration);
        mr.init();
        return mr;
    }

}
