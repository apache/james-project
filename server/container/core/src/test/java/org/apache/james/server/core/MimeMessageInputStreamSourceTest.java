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
package org.apache.james.server.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import jakarta.mail.MessagingException;

import org.apache.james.util.io.ZeroedInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class MimeMessageInputStreamSourceTest {

    private static final int _1M = 1024 * 1024;
    private static final int _10KB = 10 * 1024;
    private MimeMessageInputStreamSource testee;

    @AfterEach
    public void tearDown() {
        testee.dispose();
    }
    
    @Test
    public void streamWith1MBytesShouldBeReadable() throws MessagingException, IOException {
        testee = MimeMessageInputStreamSource.create("myKey", new ZeroedInputStream(_1M));
        assertThat(testee.getInputStream()).hasSameContentAs(new ZeroedInputStream(_1M));
    }
    
    @Test
    public void streamWith10KBytesShouldBeReadable() throws MessagingException, IOException {
        testee = MimeMessageInputStreamSource.create("myKey", new ZeroedInputStream(_10KB));
        assertThat(testee.getInputStream()).hasSameContentAs(new ZeroedInputStream(_10KB));
    }

    @Test
    public void streamWithVeryShortNameShouldWork() throws MessagingException, IOException {
        String veryShortName = "1";
        testee = MimeMessageInputStreamSource.create(veryShortName, new ZeroedInputStream(_1M));
        assertThat(testee.getInputStream()).isNotNull();
    }
}
