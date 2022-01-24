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

package org.apache.james.protocols.pop3.core;

import java.util.List;
import java.util.Optional;

import org.apache.james.protocols.api.Request;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class POP3MessageCommandArguments {
    
    private final int messageNumber;
    private final Optional<Integer> lineCount;

    public static Optional<POP3MessageCommandArguments> fromRequest(Request request) {
        String parameters = request.getArgument();
        if (parameters == null) {
            return Optional.empty();
        }

        try {
            List<Integer> args = Splitter.on(' ')
                .omitEmptyStrings()
                .trimResults()
                .splitToList(parameters)
                .stream()
                .map(Integer::parseInt)
                .collect(ImmutableList.toImmutableList());
            
            if (args.size() == 2) {
                return Optional.of(new POP3MessageCommandArguments(args.get(0), Optional.of(args.get(1))));
            } else if (args.size() == 1) {
                return Optional.of(new POP3MessageCommandArguments(args.get(0), Optional.empty()));
            } else {
                return Optional.empty();
            }
        } catch (NumberFormatException nfe) {
            return Optional.empty();
        }
    }
    
    private POP3MessageCommandArguments(int messsageNumber, Optional<Integer> lineCount) {
        this.messageNumber = messsageNumber;
        this.lineCount = lineCount;
    }
    
    int getMessageNumber() {
        return messageNumber; 
    }
    
    Optional<Integer> getLineCount() {
        return lineCount;
    }
}
