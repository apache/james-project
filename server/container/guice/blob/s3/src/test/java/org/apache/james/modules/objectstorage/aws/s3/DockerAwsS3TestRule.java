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

package org.apache.james.modules.objectstorage.aws.s3;

import org.apache.james.GuiceModuleTestRule;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Container;
import org.apache.james.blob.objectstorage.aws.TestS3Module;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.inject.Module;

public class DockerAwsS3TestRule implements GuiceModuleTestRule {

    public static final DockerAwsS3Container S3_CONTAINER = new DockerAwsS3Container();

    public DockerAwsS3TestRule() {
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                ensureAwsS3started();
                base.evaluate();
            }
        };
    }

    private void ensureAwsS3started() {
        start();
    }

    @Override
    public void await() {
    }

    @Override
    public Module getModule() {
        return new TestS3Module(S3_CONTAINER);
    }

    public void start() {
        if (!S3_CONTAINER.getRawContainer().isRunning()) {
            S3_CONTAINER.start();
        }
    }

    public void stop() {
        //nothing to stop
    }
}

