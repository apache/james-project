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

package org.apache.james.imap.decode.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.imap.message.request.SetQuotaRequest;
import org.apache.james.protocols.imap.DecodingException;
import org.junit.Test;

/**
 * SETQUOTA command parser test...
 */
public class SetQuotaCommandParserTest {

    @Test
    public void testQuotaParsing() throws DecodingException {
        SetQuotaCommandParser parser = new SetQuotaCommandParser();
        ImapCommand command = ImapCommand.anyStateCommand("Command");
        String commandString = "quotaRoot (STORAGE 512) ( MESSAGE  1024  ) \n";
        InputStream inputStream = new ByteArrayInputStream(commandString.getBytes());
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, null);
        SetQuotaRequest request = (SetQuotaRequest) parser.decode(command, lineReader, "A003", null);
        assertThat(request.getQuotaRoot()).isEqualTo("quotaRoot");
        List<SetQuotaRequest.ResourceLimit> list = request.getResourceLimits();
        assertThat(list.get(0).getResource()).isEqualTo("STORAGE");
        assertThat(list.get(0).getLimit()).isEqualTo(512);
        assertThat(list.get(1).getResource()).isEqualTo("MESSAGE");
        assertThat(list.get(1).getLimit()).isEqualTo(1024);
        assertThat(list.size()).isEqualTo(2);
    }

}
