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


package org.apache.james.mailets.configuration;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class MailetContainer implements SerializableAsXml {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String postmaster;
        private int threads;
        private ImmutableList.Builder<ProcessorConfiguration> processors;

        private Builder() {
            processors = ImmutableList.builder();
        }

        public Builder postmaster(String postmaster) {
            this.postmaster = postmaster;
            return this;
        }

        public Builder threads(int threads) {
            this.threads = threads;
            return this;
        }

        public Builder addProcessor(ProcessorConfiguration processorConfiguration) {
            this.processors.add(processorConfiguration);
            return this;
        }

        public Builder addProcessor(ProcessorConfiguration.Builder processorConfiguration) {
            this.processors.add(processorConfiguration.build());
            return this;
        }

        public MailetContainer build() {
            Preconditions.checkState(!Strings.isNullOrEmpty(postmaster), "'postmaster' is mandatory");
            Preconditions.checkState(threads > 0, "'threads' should be greater than 0");
            return new MailetContainer(postmaster, threads, processors.build());
        }
    }

    private final String postmaster;
    private final int threads;
    private final List<ProcessorConfiguration> processors;

    private MailetContainer(String postmaster, int threads, List<ProcessorConfiguration> processors) {
        this.postmaster = postmaster;
        this.threads = threads;
        this.processors = processors;
    }

    public String getPostmaster() {
        return postmaster;
    }

    public int getThreads() {
        return threads;
    }

    public List<ProcessorConfiguration> getProcessors() {
        return processors;
    }

    @Override
    public String serializeAsXml() {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\"?>\n")
            .append("<mailetcontainer enableJmx=\"true\">\n")
            .append("<context><postmaster>").append(getPostmaster()).append("</postmaster>").append("</context>\n")
            .append("<spooler><threads>").append(getThreads()).append("</threads>").append("</spooler>\n")
            .append("<processors>\n");
        for (ProcessorConfiguration processorConfiguration : getProcessors()) {
            builder.append(processorConfiguration.serializeAsXml());
        }
        builder.append("</processors>\n")
            .append("</mailetcontainer>");
        return builder.toString();
    }
}
