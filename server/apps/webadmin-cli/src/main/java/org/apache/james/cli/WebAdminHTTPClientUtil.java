/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.cli;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Optional;
import java.util.Scanner;

import org.apache.james.webadmin.httpclient.WebAdminHTTPClientFactory;

import feign.Feign;

public class WebAdminHTTPClientUtil {

    public static class JwtToken {
        final Optional<String> jwtTokenString;

        final Optional<String> jwtFilePath;

        PrintStream err;

        public JwtToken(Optional<String> jwtTokenString, Optional<String> jwtFilePath, PrintStream err) {
            this.jwtTokenString = jwtTokenString;
            this.jwtFilePath = jwtFilePath;
            this.err = err;
        }
    }

    private final JwtToken jwtToken;

    public WebAdminHTTPClientUtil(JwtToken jwtToken) {
        this.jwtToken = jwtToken;
    }

    public Feign.Builder builder() {
        return jwtOptionsHandler();
    }

    private Feign.Builder jwtOptionsHandler() {
        return jwtToken.jwtTokenString
            .map(tokenString -> jwtToken.jwtFilePath
                .map(tokenFile -> jwtTokenAndJwtFileAreBothPresentHandler())
                .orElse(jwtTokenIsPresentAndJwtFromFileIsNotPresentHandler(tokenString)))
            .orElse(jwtToken.jwtFilePath
                .map(this::jwtTokenIsNotPresentAndJwtFromFileIsPresentHandler)
                .orElse(jwtTokenAndJwtFromFileAreNotPresentHandler()));
    }

    private Feign.Builder jwtTokenAndJwtFileAreBothPresentHandler() {
        jwtToken.err.println("Cannot specify both --jwt-from-file and --jwt-token options.");
        return WebAdminHTTPClientFactory.feignBuilder();
    }

    private Feign.Builder jwtTokenIsPresentAndJwtFromFileIsNotPresentHandler(String tokenString) {
        return WebAdminHTTPClientFactory.feignBuilder(tokenString);
    }

    private Feign.Builder jwtTokenIsNotPresentAndJwtFromFileIsPresentHandler(String tokenFile) {
        File myObj = new File(tokenFile);
        try (Scanner myReader = new Scanner(myObj)) {
            StringBuilder data = new StringBuilder();
            while (myReader.hasNextLine()) {
                data.append(myReader.nextLine());
            }
            return WebAdminHTTPClientFactory.feignBuilder(data.toString());
        } catch (FileNotFoundException e) {
            e.printStackTrace(jwtToken.err);
            return Feign.builder();
        }
    }

    private Feign.Builder jwtTokenAndJwtFromFileAreNotPresentHandler() {
        return WebAdminHTTPClientFactory.feignBuilder();
    }

}
