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

import jakarta.inject.Inject;

import org.apache.activemq.blob.BlobDownloadStrategy;
import org.apache.activemq.blob.BlobTransferPolicy;
import org.apache.activemq.blob.BlobUploadStrategy;
import org.apache.james.filesystem.api.FileSystem;

/**
 * {@link BlobTransferPolicy} which use the {@link FileSystem} to download and
 * upload data. So this implementation is only useful when using a non-clustered
 * ActiveMQ Broker or when using a shared Storage for the files.
 */
public class FileSystemBlobTransferPolicy extends BlobTransferPolicy {

    private FileSystem fileSystem;
    private int splitCount = 10;
    private FileSystemBlobStrategy strategy;

    @Inject
    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public void setSplitCount(int splitCount) {
        this.splitCount = splitCount;
    }

    @Override
    public BlobTransferPolicy copy() {
        FileSystemBlobTransferPolicy that = new FileSystemBlobTransferPolicy();
        that.setFileSystem(fileSystem);
        that.setDefaultUploadUrl(getDefaultUploadUrl());
        that.setBrokerUploadUrl(getBrokerUploadUrl());
        that.setUploadUrl(getUploadUrl());
        that.setUploadStrategy(getUploadStrategy());
        return that;
    }

    @Override
    protected BlobDownloadStrategy createDownloadStrategy() {
        return getStrategy();
    }

    @Override
    protected BlobUploadStrategy createUploadStrategy() {
        return getStrategy();
    }

    private synchronized FileSystemBlobStrategy getStrategy() {
        if (strategy == null) {
            strategy = new FileSystemBlobStrategy(this, fileSystem, splitCount);
        }
        return strategy;
    }
}
