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

import static org.apache.james.modules.TestJMAPServerModule.LIMIT_TO_3_MESSAGES;

import java.io.IOException;

import org.apache.james.GuiceJamesServer;
import org.apache.james.MemoryJmapTestRule;
import org.apache.james.jmap.draft.methods.integration.GetMessageListMethodTest;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class MemoryGetMessageListMethodTest extends GetMessageListMethodTest {

    @Rule
    public MemoryJmapTestRule memoryJmap = new MemoryJmapTestRule();

    @Override
    protected GuiceJamesServer createJmapServer() throws IOException {
        return memoryJmap.jmapServer(TestJMAPServerModule.maximumMessages(LIMIT_TO_3_MESSAGES));
    }
    
    @Override
    protected void await() {

    }

    @Override
    @Ignore("This feature is not supported by MessageSearchIndex implementation binded in the Memory product")
    @Test
    public void getMessageListShouldIncludeMessagesWhenTextFilterMatchesBodyWithStemming() {
    }

    @Override
    @Ignore("JAMES-2756 Memory James Server uses the SimpleMessageSearchIndex, " +
        "it doesn't support to search on the encoded header value's names")
    @Test
    public void searchByFromFieldShouldSupportUTF8FromName() {
    }
}
