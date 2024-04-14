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

package org.apache.james.modules;

import java.io.FileNotFoundException;
import java.util.Optional;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.jmap.JMAPConfiguration;
import org.apache.james.jwt.JwtConfiguration;
import org.apache.james.jwt.JwtTokenVerifier;
import org.apache.james.modules.mailbox.FastRetryBackoffModule;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class TestJMAPServerModule extends AbstractModule {

    private static final String PUBLIC_PEM_KEY =
        "-----BEGIN PUBLIC KEY-----\n" +
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA8RfttZlaFNar/3GcU9RG\n" +
        "3/2ZGi7PMI8B75XaX3AG2J1Mvl30Dl+yZ8tQKGGd/eelQsQAQboGXCHB/wCHR7n8\n" +
        "CSF/E0hjRu6FK/2D6VTnn5BMiiPWPcEpLGT7CsGQPFZ54R0e5vJcGTty9Da/3Z+C\n" +
        "YvrpupecrmJXCXwBwhm4WPb1woTfv9gVtTMpKOhfzmrGURy8nJ+1s4Lli46/UBIu\n" +
        "fzTmzng4rXouLMvkq7eS4jIWtB0ifXHmPwRN/YhGmuq+GYG59KQ1iStSxVi9Nuzy\n" +
        "Ik8eH3KeLx/2BuSshSwnnnYbxHEfR+p6H4oqKjTGutvLNamBRFKpj7CcryKbgVG0\n" +
        "zwIDAQAB\n" +
        "-----END PUBLIC KEY-----\n";

    // For commodity, here is the matching private key, allowing to generate easily token if needed
    // for example using jwt.io.
    @SuppressWarnings("unused")
    private static final String UNUSED_PRIVATE_KEY =
        "-----BEGIN RSA PRIVATE KEY-----\n" +
            "MIIEogIBAAKCAQEA8RfttZlaFNar/3GcU9RG3/2ZGi7PMI8B75XaX3AG2J1Mvl30\n" +
            "Dl+yZ8tQKGGd/eelQsQAQboGXCHB/wCHR7n8CSF/E0hjRu6FK/2D6VTnn5BMiiPW\n" +
            "PcEpLGT7CsGQPFZ54R0e5vJcGTty9Da/3Z+CYvrpupecrmJXCXwBwhm4WPb1woTf\n" +
            "v9gVtTMpKOhfzmrGURy8nJ+1s4Lli46/UBIufzTmzng4rXouLMvkq7eS4jIWtB0i\n" +
            "fXHmPwRN/YhGmuq+GYG59KQ1iStSxVi9NuzyIk8eH3KeLx/2BuSshSwnnnYbxHEf\n" +
            "R+p6H4oqKjTGutvLNamBRFKpj7CcryKbgVG0zwIDAQABAoIBADGzIxeag01ka5R/\n" +
            "ESDe07V9C8CwAZobAOUo2RlveJnS420i5RrJc3eeG+oXJYCf7htzWDI0bPc1Jk6x\n" +
            "BzIsDt66/v00oPKQXPeSjUzeadkk4AJiHNoiJaC3OGEhQeCOWxWi8SnesEmrTak2\n" +
            "WBsRtMk+vEvw0SXJs/OKkro2nyAHcm7qOkm/EDeyZFqXGyWlkx4iLiVgmhe33X62\n" +
            "WxrLqAhKSqwwta3df3RQ1lPSlOxe1Jikg39NI57AblJ9VqQBdyVJRcMbVmZXBlan\n" +
            "uFFHRBOnVR5Fg6DJZaFXAsXweduvw9N5mRMEIXX3YiLRsOdKTKm6bBCorfSi5QNa\n" +
            "aQZhmrECgYEA/0iXxv5GxI5Ycv3yQni/Qu4GSE5qLFeFAfXhWiv73WZlqjNONgq7\n" +
            "y2uBTojnI93yNGp8rcbI+9t+MTr0eIkKzo7ftYt0SsDshjLcO/pxuT4qWXfDO87b\n" +
            "AGgfyuAugAE67KMuwxqQuT6NpGgYVhRn6JUYjVzT13kKzEPv7IOw7aUCgYEA8cUk\n" +
            "EE4uVBfpObsFd9u9KL2tSFCBuKZZH8FqDdcj3lfdizwqd7D4kmVr4vFDtG1rtqOm\n" +
            "qyNqI6eMr6wvqExPrLaf1lHlpYyHeW4t2S2iHh4tKriwcoPMUpuJHUcYHHzzCSux\n" +
            "Kbk1cDFGphTOt4AnLk8HAivZWNQLieLaOQmhNmMCgYAYLogyEWQiulkmi2enZEi5\n" +
            "zlJKByOHj8LJrMDsCb6R+mEm/jUqaVngqw5UoiNDAoMu8+dbjrj7Io+RmkQOJu0f\n" +
            "I+mNCOi7LAs7qxWxmMetBHZ+gxm7UJzuLO7WCOZeub8bK1oCoUGUSpigOjwT61rs\n" +
            "bTMmMOTgRFcBgm33uYHJAQKBgA8uNLR9ZDVNhwxj7NT4zCjJuB6pR2vjrgbraxBR\n" +
            "aOQmGjgK4BPB3em7SonmYjzq/e9q2SU3xQtWEuRY6Gkl2X7bvK+FVukNKNh8DY7s\n" +
            "aZiAho9/Jz0Zf3PUZkibVS08vzndL3OSOIPB5FC7T7t/5XXn6mW9gRktv0e6Ib+h\n" +
            "FilHAoGAMA32OsmvG+tHam8FjrUgNNyBrdO2yfFNOWLDVt9CYIYSQa4dVpUKAyOH\n" +
            "CF52SG6Ki1L0z001yrJ7gBk/i6KD9h+7izzs+16DSbWTPB3R16BVjRVoXgmg0vh5\n" +
            "ICQil1aaN7/2au+p7E4n7nzfYG7nRX5syDoqgBbdhpJxV8/5ohA=\n" +
            "-----END RSA PRIVATE KEY-----\n";


    @Override
    protected void configure() {
        install(new FastRetryBackoffModule());
    }

    @Provides
    @Singleton
    JMAPConfiguration provideConfiguration() throws FileNotFoundException, ConfigurationException {
        return JMAPConfiguration.builder()
            .enable()
            .randomPort()
            .enableEmailQueryView()
            .maximumSendSize(Optional.of(10L * 1024L * 1024L)) // 10 MB
            .build();
    }

    @Provides
    @Singleton
    @Named("jmap")
    JwtTokenVerifier providesJwtTokenVerifier() {
        return JwtTokenVerifier.create(new JwtConfiguration(ImmutableList.of(PUBLIC_PEM_KEY)));
    }
}
