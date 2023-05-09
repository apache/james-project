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

package org.apache.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import java.io.IOException;

import org.apache.james.modules.TestJMAPServerModule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.inject.Module;

public class MemoryJmapTestRule implements TestRule {

    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    public GuiceJamesServer jmapServer(Module... modules) throws IOException {
        MemoryJamesConfiguration configuration = MemoryJamesConfiguration.builder()
            .enableJMAP()
            .workingDirectory(temporaryFolder.newFolder())
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build();
        return MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(modules);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return temporaryFolder.apply(base, description);
    }
    
}
