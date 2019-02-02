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
package org.apache.james.mailbox.tools.jpa.migrator;

import org.junit.Ignore;
import org.junit.Test;

@Ignore("This class needs to be reviewed")
public class JpaMigratorTest {
    @Test
    public void testImap165() throws Exception {
        JpaMigrator.main(new String[]{"IMAP165"});
    }

    @Test
    public void testImap168() throws Exception {
        JpaMigrator.main(new String[]{"IMAP168"});
    }

    @Test
    public void testImap172() throws Exception {
        JpaMigrator.main(new String[]{"IMAP172"});
    }

    @Test
    public void testImap176() throws Exception {
        JpaMigrator.main(new String[]{"IMAP176"});
    }

    @Test
    public void testImap180() throws Exception {
        JpaMigrator.main(new String[]{"IMAP180"});
    }

    @Test
    public void testImap184() throws Exception {
        JpaMigrator.main(new String[]{"IMAP184"});
    }
}
