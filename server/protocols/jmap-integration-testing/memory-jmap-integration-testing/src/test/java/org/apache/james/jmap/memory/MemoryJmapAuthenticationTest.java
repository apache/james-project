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

package org.apache.james.jmap.memory;

import org.apache.james.MemoryJamesServer;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.jmap.FixedDateZonedDateTimeProvider;
import org.apache.james.jmap.JMAPAuthenticationTest;
import org.apache.james.jmap.servers.MemoryJmapServerModule;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class MemoryJmapAuthenticationTest extends JMAPAuthenticationTest<MemoryJamesServer> {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Override
    protected MemoryJamesServer createJmapServer(FixedDateZonedDateTimeProvider zonedDateTimeProvider) {
        return new MemoryJamesServer()
                .combineWith(MemoryJamesServerMain.inMemoryServerModule)
                .overrideWith(new MemoryJmapServerModule(temporaryFolder),
                             (binder) -> binder.bind(ZonedDateTimeProvider.class).toInstance(zonedDateTimeProvider));
    }
}
