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

package org.apache.james.mpt.host;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.james.managesieve.api.SessionTerminatedException;
import org.apache.james.managesieve.transcode.ManageSieveProcessor;
import org.apache.james.managesieve.util.SettableSession;
import org.apache.james.mpt.api.Continuation;
import org.apache.james.mpt.api.Session;
import org.apache.james.mpt.helper.ByteBufferInputStream;
import org.apache.james.mpt.helper.ByteBufferOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManageSieveSession implements Session {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManageSieveSession.class);

    private final ByteBufferOutputStream out;
    private final ByteBufferInputStream in;
    private final ManageSieveProcessor manageSieveProcessor;
    private final SettableSession settableSession;
    private boolean isReadLast = true;

    public ManageSieveSession(ManageSieveProcessor manageSieveProcessor, Continuation continuation) {
        this.manageSieveProcessor = manageSieveProcessor;
        this.out = new ByteBufferOutputStream(continuation);
        this.in = new ByteBufferInputStream();
        this.settableSession = new SettableSession();
    }

    @Override
    public String readLine() throws Exception {
        if (!isReadLast) {
            String response;
            StringWriter stringWriter = new StringWriter();
            IOUtils.copy(in, stringWriter, StandardCharsets.UTF_8);
            String request = stringWriter.toString();
            try {
                response = manageSieveProcessor.handleRequest(settableSession, request);
            } catch (SessionTerminatedException e) {
                LOGGER.info("Session is terminated");
                response = "OK channel is closing\r\n";
            }
            out.write(response);
            isReadLast = true;
        }
        if (settableSession.getState() == org.apache.james.managesieve.api.Session.State.SSL_NEGOCIATION) {
            settableSession.setState(org.apache.james.managesieve.api.Session.State.UNAUTHENTICATED);
            settableSession.setSslEnabled(true);
        }
        return out.nextLine();
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void restart() {
    }

    @Override
    public void await() {

    }

    @Override
    public void writeLine(String line) {
        isReadLast = false;
        in.nextLine(line);
    }
}
