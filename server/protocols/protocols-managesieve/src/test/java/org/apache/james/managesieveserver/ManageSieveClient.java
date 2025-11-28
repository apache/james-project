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

package org.apache.james.managesieveserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.net.SocketClient;
import org.apache.commons.net.io.CRLFLineReader;

public class ManageSieveClient extends SocketClient {
    private static final String ENCODING = StandardCharsets.UTF_8.name();

    enum ResponseType {
        BYE,
        CONTINUATION,
        NO,
        OK;
    }

    record ServerResponse(
        ResponseType responseType,
        Optional<String> responseCode,
        Optional<String> explanation,
        ArrayList<String> responseLines
    ) {}

    private BufferedReader reader;
    private BufferedWriter writer;

    @Override
    protected void _connectAction_() throws IOException {
        super._connectAction_();
        this.reader = new CRLFLineReader(new InputStreamReader(_input_, ENCODING));
        this.writer = new BufferedWriter(new OutputStreamWriter(_output_, ENCODING));
    }

    @Override
    public void disconnect() throws IOException {
        super.disconnect();
        this.reader = null;
        this.writer = null;
    }

    public ServerResponse readResponse() throws IOException {
        ServerResponse response = null;
        ArrayList<String> lines = new ArrayList<>();
        while (response == null) {
            String line = this.reader.readLine();
            String[] tokens = line.split(" ", 3);
            if (EnumUtils.isValidEnumIgnoreCase(ResponseType.class, tokens[0])) {
                ResponseType responseType = EnumUtils.getEnumIgnoreCase(ResponseType.class, tokens[0]);
                Optional<String> responseCode = Optional.empty();
                Optional<String> explanation = Optional.empty();
                if (tokens.length == 2 && tokens[1].startsWith("(")) {
                    responseCode = Optional.of(tokens[1].substring(1, tokens[1].length() - 1));
                } else if (tokens.length == 2 && !tokens[1].startsWith("(")) {
                    explanation = Optional.of(tokens[1]);
                } else if (tokens.length == 3 && tokens[1].startsWith("(")) {
                    responseCode = Optional.of(tokens[1].substring(1, tokens[1].length() - 1));
                    explanation = Optional.of(tokens[2]);
                } else if (tokens.length == 3 && !tokens[1].startsWith("(")) {
                    explanation = Optional.of(tokens[1] + " " + tokens[2]);
                }
                if (explanation.isPresent() && explanation.get().charAt(0) == '"' && explanation.get().charAt(explanation.get().length() - 1) == '"') {
                    explanation = Optional.of(explanation.get().substring(1, explanation.get().length() - 1));
                }

                response = new ServerResponse(responseType, responseCode, explanation, lines);
            } else if (tokens[0].equals("+")) {
                Optional explanation = Optional.of(tokens[1].substring(1, tokens[1].length() - 1));
                response = new ServerResponse(ResponseType.CONTINUATION, Optional.empty(), explanation, new ArrayList());
            } else {
                lines.addLast(line);
            }
        }
        return response;
    }

    public void sendCommand(String command) throws IOException {
        this.writer.write(command + "\r\n");
        this.writer.flush();
    }
}
