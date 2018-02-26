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

package org.apache.james.util.scanner;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.testcontainers.containers.GenericContainer;

import com.github.fge.lambdas.Throwing;

public class SpamAssassinExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

    private final GenericContainer<?> spamAssassinContainer;
    private SpamAssassin spamAssassin;

    public SpamAssassinExtension() {
        spamAssassinContainer = new GenericContainer<>("dinkel/spamassassin:3.4.0");
        spamAssassinContainer.waitingFor(new SpamAssassinWaitStrategy());
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        spamAssassinContainer.start();
        spamAssassin = new SpamAssassin(spamAssassinContainer);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        spamAssassinContainer.close();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == SpamAssassin.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return spamAssassin;
    }
    
    public static class SpamAssassin {
        
        private static final int SPAMASSASSIN_PORT = 783;

        private final String ip;
        private final int bindingPort;
        private final GenericContainer<?> spamAssassinContainer;

        private SpamAssassin(GenericContainer<?> spamAssassinContainer) {
            this.spamAssassinContainer = spamAssassinContainer;
            this.ip = spamAssassinContainer.getContainerIpAddress();
            this.bindingPort = spamAssassinContainer.getMappedPort(SPAMASSASSIN_PORT);
        }

        public String getIp() {
            return ip;
        }
    
        public int getBindingPort() {
            return bindingPort;
        }

        public void train() throws IOException, URISyntaxException {
            train(Paths.get(ClassLoader.getSystemResource("spamassassin_db/spam").toURI()), TrainingKind.SPAM);
            train(Paths.get(ClassLoader.getSystemResource("spamassassin_db/ham").toURI()), TrainingKind.HAM);
        }

        private void train(Path folder, TrainingKind trainingKind) throws URISyntaxException, IOException {
            try (Stream<Path> paths = Files.walk(folder)) {
                paths
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .map(Throwing.function(FileInputStream::new))
                    .map(readAsPair())
                    .map(closeStream())
                    .map(Pair::getRight)
                    .forEach(Throwing.consumer(message -> 
                            spamAssassinContainer.execInContainer("sa-learn", 
                                    trainingKind.saLearnExtensionName(), 
                                    message)));
            }
        }

        private Function<InputStream, Pair<InputStream, String>> readAsPair() {
            return Throwing.function(inputStream -> Pair.of(inputStream, IOUtils.toString(inputStream, StandardCharsets.UTF_8)));
        }

        private Function<Pair<InputStream, String>, Pair<InputStream, String>> closeStream() {
            return Throwing.function(pair -> { 
                pair.getLeft().close();
                return pair;
            });
        }

        private static enum TrainingKind {
            SPAM("--spam"), HAM("--ham");

            private String saLearnExtensionName;

            TrainingKind(String saLearnExtensionName) {
                this.saLearnExtensionName = saLearnExtensionName;
            }

            public String saLearnExtensionName() {
                return saLearnExtensionName;
            }
        }
    }

}
