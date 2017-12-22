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
package org.apache.james.queue.activemq;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.io.FileUtils;
import org.apache.james.filesystem.api.FileSystem;

import com.google.common.base.Throwables;

public class ActiveMQMailQueueBlobTest extends ActiveMQMailQueueTest {

    public static final String BASE_DIR = "file://target/james-test";
    private MyFileSystem fs;

    @Override
    protected ActiveMQConnectionFactory createConnectionFactory() {
        ActiveMQConnectionFactory factory = super.createConnectionFactory();

        FileSystemBlobTransferPolicy policy = new FileSystemBlobTransferPolicy();
        policy.setFileSystem(fs);
        policy.setDefaultUploadUrl(BASE_DIR);
        factory.setBlobTransferPolicy(policy);

        return factory;
    }

    @Override
    public void setUp() throws Exception {
        fs = new MyFileSystem();
        super.setUp();
    }

    public void tearDown() throws Exception {
        if (fs != null) {
            fs.destroy();
        }
    }

    @Override
    protected boolean useBlobMessages() {
        return true;
    }

    private final class MyFileSystem implements FileSystem {

        @Override
        public InputStream getResource(String url) throws IOException {
            return null;
        }

        @Override
        public File getFile(String fileURL) throws FileNotFoundException {
            if (fileURL.startsWith("file://")) {
                return new File(fileURL.substring("file://".length()));

            } else if (fileURL.startsWith("file:/")) {
                return new File(fileURL.substring("file:".length()));

            }
            throw new FileNotFoundException();
        }

        @Override
        public File getBasedir() throws FileNotFoundException {
            throw new FileNotFoundException();
        }

        public void destroy() throws FileNotFoundException {
            try {
                FileUtils.forceDelete(getFile(BASE_DIR));
            } catch (FileNotFoundException e) {
                throw e;
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }
    }
}
