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

package org.apache.james.vault.metadata;

import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.MODULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class UserPerBucketDAOTest {
    private static final BucketName BUCKET_NAME = BucketName.of("deletedMessages-2019-06-01");
    private static final BucketName BUCKET_NAME_2 = BucketName.of("deletedMessages-2019-07-01");
    private static final Username OWNER = Username.of("owner");
    private static final Username OWNER_2 = Username.of("owner2");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    private UserPerBucketDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new UserPerBucketDAO(cassandra.getConf());
    }

    @Test
    void retrieveUsersShouldReturnEmptyWhenNone() {
        assertThat(testee.retrieveUsers(BUCKET_NAME).toStream()).isEmpty();
    }

    @Test
    void retrieveBucketsShouldReturnEmptyWhenNone() {
        assertThat(testee.retrieveBuckets().toStream()).isEmpty();
    }

    @Test
    void retrieveUsersShouldReturnAddedUser() {
        testee.addUser(BUCKET_NAME, OWNER).block();

        assertThat(testee.retrieveUsers(BUCKET_NAME).toStream()).containsExactly(OWNER);
    }

    @Test
    void retrieveBucketsShouldReturnAddedBuckets() {
        testee.addUser(BUCKET_NAME, OWNER).block();

        assertThat(testee.retrieveBuckets().toStream()).containsExactly(BUCKET_NAME);
    }

    @Test
    void retrieveUsersShouldReturnAddedUsers() {
        testee.addUser(BUCKET_NAME, OWNER).block();
        testee.addUser(BUCKET_NAME, OWNER_2).block();

        assertThat(testee.retrieveUsers(BUCKET_NAME).toStream()).containsExactlyInAnyOrder(OWNER, OWNER_2);
    }

    @Test
    void retrieveBucketsShouldNotReturnDuplicates() {
        testee.addUser(BUCKET_NAME, OWNER).block();
        testee.addUser(BUCKET_NAME, OWNER_2).block();

        assertThat(testee.retrieveBuckets().toStream()).containsExactly(BUCKET_NAME);
    }

    @Test
    void retrieveUsersShouldNotReturnUsersOfOtherBuckets() {
        testee.addUser(BUCKET_NAME, OWNER).block();
        testee.addUser(BUCKET_NAME_2, OWNER_2).block();

        assertThat(testee.retrieveUsers(BUCKET_NAME).toStream()).containsExactlyInAnyOrder(OWNER);
    }

    @Test
    void retrieveBucketsShouldReturnAllAddedBuckets() {
        testee.addUser(BUCKET_NAME, OWNER).block();
        testee.addUser(BUCKET_NAME_2, OWNER_2).block();

        assertThat(testee.retrieveUsers(BUCKET_NAME).toStream()).containsExactlyInAnyOrder(OWNER);
    }

    @Test
    void addUserShouldBeIdempotent() {
        testee.addUser(BUCKET_NAME, OWNER).block();
        testee.addUser(BUCKET_NAME, OWNER).block();

        assertThat(testee.retrieveUsers(BUCKET_NAME).toStream()).containsExactlyInAnyOrder(OWNER);
    }

    @Test
    void retrieveUsersShouldReturnEmptyWhenDeletedBucket() {
        testee.addUser(BUCKET_NAME, OWNER).block();

        testee.deleteBucket(BUCKET_NAME).block();

        assertThat(testee.retrieveUsers(BUCKET_NAME).toStream()).isEmpty();
    }

    @Test
    void deleteBucketShouldNotThrowWhenNone() {
        assertThatCode(() -> testee.deleteBucket(BUCKET_NAME).block())
            .doesNotThrowAnyException();
    }
}