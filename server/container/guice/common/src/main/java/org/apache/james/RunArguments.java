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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class RunArguments {

    public static RunArguments empty() {
        return new RunArguments(Set.of());
    }

    public static RunArguments from(String[] args) {
        return new RunArguments(Arrays.stream(args)
            .map(Argument::parse)
            .collect(Collectors.toSet()));
    }

    public enum Argument {
        START_DEV;

        public static Argument parse(String arg) {
            switch (arg) {
                case "start-dev":
                    return START_DEV;
                default:
                    throw new IllegalArgumentException("Invalid running argument: " + arg);
            }
        }
    }

    private final Set<Argument> arguments;

    public RunArguments(Set<Argument> arguments) {
        this.arguments = arguments;
    }

    public Set<Argument> getArguments() {
        return arguments;
    }

    public boolean containStartDev() {
        return getArguments().contains(Argument.START_DEV);
    }
}
