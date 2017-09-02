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

package org.apache.james.ai.classic;

import java.io.IOException;
import java.io.Reader;
import java.util.stream.IntStream;

/**
 * Tokenizes streaming mail input.
 */
public abstract class Tokenizer {

    private String header = "";
   
    /**
     * Tokenizes a stream.
     * 
     * @param stream not null
     */
    protected void doTokenize(Reader stream) throws IOException {

        String token;
        while ((token = nextToken(stream)) != null) {
            boolean endingLine = false;
            if (token.length() > 0 && token.charAt(token.length() - 1) == '\n') {
                endingLine = true;
                token = token.substring(0, token.length() - 1);
            }

            if (token.length() > 0 && header.length() + token.length() < 90 && !allDigits(token)) {
                if (token.equals("From:") || token.equals("Return-Path:") || token.equals("Subject:") || token.equals("To:")) {
                    header = token;
                    if (!endingLine) {
                        continue;
                    }
                }

                token = header + token;

                next(token);
            }

            if (endingLine) {
                header = "";
            }
        }
    }
    
    /**
     * Process next token.
     * @param token not null
     */
    protected abstract void next(String token);

    private boolean allDigits(String s) {
        return IntStream.range(0, s.length())
            .allMatch(i -> Character.isDigit(s.charAt(i)));
    }
    
    private String nextToken(Reader reader) throws java.io.IOException {
        StringBuilder token = new StringBuilder();
        int i;
        char ch, ch2;
        boolean previousWasDigit = false;
        boolean tokenCharFound = false;

        if (!reader.ready()) {
            return null;
        }

        while ((i = reader.read()) != -1) {

            ch = (char) i;

            if (ch == ':') {
                String tokenString = token.toString() + ':';
                if (tokenString.equals("From:") || tokenString.equals("Return-Path:") || tokenString.equals("Subject:") || tokenString.equals("To:")) {
                    return tokenString;
                }
            }

            if (Character.isLetter(ch) || ch == '-' || ch == '$' || ch == '\u20AC' // the
                                                                                   // EURO
                                                                                   // symbol
                    || ch == '!' || ch == '\'') {
                tokenCharFound = true;
                previousWasDigit = false;
                token.append(ch);
            } else if (Character.isDigit(ch)) {
                tokenCharFound = true;
                previousWasDigit = true;
                token.append(ch);
            } else if (previousWasDigit && (ch == '.' || ch == ',')) {
                reader.mark(1);
                previousWasDigit = false;
                i = reader.read();
                if (i == -1) {
                    break;
                }
                ch2 = (char) i;
                if (Character.isDigit(ch2)) {
                    tokenCharFound = true;
                    previousWasDigit = true;
                    token.append(ch);
                    token.append(ch2);
                } else {
                    reader.reset();
                    break;
                }
            } else if (ch == '\r') { //NOPMD
                // cr found, ignore
            } else if (ch == '\n') {
                // eol found
                tokenCharFound = true;
                previousWasDigit = false;
                token.append(ch);
                break;
            } else if (tokenCharFound) {
                break;
            }
        }

        if (tokenCharFound) {
            // System.out.println("Token read: " + token);
            return token.toString();
        } else {
            return null;
        }
    }
}
