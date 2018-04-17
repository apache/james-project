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
package org.apache.james.user.hbase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.james.system.hbase.TablePool;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.hbase.def.HUsersRepository;
import org.apache.james.user.lib.AbstractUsersRepository;
import org.apache.james.user.lib.model.DefaultUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the UserRepository for a HBase persistence.
 */
public class HBaseUsersRepository extends AbstractUsersRepository {

    /**
     * The Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(HBaseUsersRepository.class.getName());
    /**
     * Hashing algorithm for the password.
     */
    private String algo;

    @Override
    public void doConfigure(HierarchicalConfiguration config) throws ConfigurationException {
        algo = config.getString("algorithm", "MD5");
        super.doConfigure(config);
    }

    @Override
    public User getUserByName(String name) throws UsersRepositoryException {
        KeyValue keyValue = getKeyValue(name);
        User user = null;
        if (keyValue != null) {
            user = new DefaultUser(Bytes.toString(keyValue.getRow()), Bytes.toString(keyValue.getValue()), algo);
        }
        return user;
    }

    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        if (user == null) {
            throw new UsersRepositoryException("Please provide a non null user");
        }
        if (!(user instanceof DefaultUser)) {
            throw new UsersRepositoryException("Please provide a user instanceof DefaultUser");
        }
        User existingUser = getUserByName(user.getUserName());
        if (existingUser == null) {
            throw new UsersRepositoryException("Please provide an existing user to update");
        }
        putUser((DefaultUser) user, false);
    }

    @Override
    public void removeUser(String name) throws UsersRepositoryException {
        HTableInterface table = null;
        try {
            table = TablePool.getInstance().getUsersRepositoryTable();
            Delete delete = new Delete(Bytes.toBytes(name));
            table.delete(delete);
            table.flushCommits();
        } catch (IOException e) {
            log.error("Error while deleting user from HBase", e);
            throw new UsersRepositoryException("Error while deleting user from HBase", e);
        } finally {
            IOUtils.closeQuietly(table);
        }
    }

    @Override
    public boolean contains(String name) throws UsersRepositoryException {
        KeyValue keyValue = getKeyValue(name.toLowerCase(Locale.US));
        return (keyValue != null);
    }

    @Override
    public boolean test(String name, String password) throws UsersRepositoryException {
        KeyValue keyValue = getKeyValue(name);
        if (keyValue != null) {
            DefaultUser user = new DefaultUser(name, algo);
            user.setPassword(password);
            return Bytes.toString(keyValue.getValue()).equals(user.getHashedPassword());
        }
        return false;
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        HTableInterface table = null;
        ResultScanner resultScanner = null;
        try {
            table = TablePool.getInstance().getUsersRepositoryTable();
            Scan scan = new Scan();
            scan.addFamily(HUsersRepository.COLUMN_FAMILY_NAME);
            scan.setCaching(table.getConfiguration().getInt("hbase.client.scanner.caching", 1) * 2);
            resultScanner = table.getScanner(scan);
            int resultCount = 0;
            while (resultScanner.next() != null) {
                resultCount++;
            }
            return resultCount;
        } catch (IOException e) {
            log.error("Error while counting users from HBase", e);
            throw new UsersRepositoryException("Error while counting users from HBase", e);
        } finally {
            IOUtils.closeQuietly(resultScanner);
            IOUtils.closeQuietly(table);
        }
    }

    @Override
    public Iterator<String> list() throws UsersRepositoryException {
        List<String> list = new ArrayList<>();
        HTableInterface table = null;
        ResultScanner resultScanner = null;
        try {
            table = TablePool.getInstance().getUsersRepositoryTable();
            Scan scan = new Scan();
            scan.addFamily(HUsersRepository.COLUMN_FAMILY_NAME);
            scan.setCaching(table.getConfiguration().getInt("hbase.client.scanner.caching", 1) * 2);
            resultScanner = table.getScanner(scan);
            Result result;
            while ((result = resultScanner.next()) != null) {
                list.add(Bytes.toString(result.getRow()));
            }
        } catch (IOException e) {
            log.error("Error while scanning users from HBase", e);
            throw new UsersRepositoryException("Error while scanning users from HBase", e);
        } finally {
            IOUtils.closeQuietly(resultScanner);
            IOUtils.closeQuietly(table);
        }
        return list.iterator();
    }

    @Override
    protected void doAddUser(String username, String password) throws UsersRepositoryException {
        DefaultUser user = new DefaultUser(username, algo);
        user.setPassword(password);
        putUser(user, true);
    }

    /**
     * Utility method to retrieve a HBase KeyValue for a given username.
     * 
     * @param username
     * @return
     * @throws UsersRepositoryException
     */
    private KeyValue getKeyValue(String username) throws UsersRepositoryException {
        HTableInterface table = null;
        try {
            table = TablePool.getInstance().getUsersRepositoryTable();
            Get get = new Get(Bytes.toBytes(username));
            Result result = table.get(get);
            KeyValue keyValue = result.getColumnLatest(HUsersRepository.COLUMN_FAMILY_NAME, HUsersRepository.COLUMN.PWD);
            return keyValue;
        } catch (IOException e) {
            log.error("Error while counting users from HBase", e);
            throw new UsersRepositoryException("Error while counting users from HBase", e);
        } finally {
            IOUtils.closeQuietly(table);
        }
    }

    /**
     * Utility method to put a User in HBase.
     * 
     * @param user
     * @throws UsersRepositoryException
     */
    private void putUser(DefaultUser user, boolean isAdd) throws UsersRepositoryException {
        String username = user.getUserName();
        if (isAdd) {
            username = user.getUserName().toLowerCase(Locale.US);
            if (contains(username)) {
                throw new UsersRepositoryException(username + " already exists.");
            }
        }
        HTableInterface table = null;
        try {
            table = TablePool.getInstance().getUsersRepositoryTable();
            Put put = new Put(Bytes.toBytes(username));
            put.add(HUsersRepository.COLUMN_FAMILY_NAME, HUsersRepository.COLUMN.PWD, 
                    Bytes.toBytes(user.getHashedPassword()));
            table.put(put);
            table.flushCommits();
        } catch (IOException e) {
            log.error("Error while adding user in HBase", e);
            throw new UsersRepositoryException("Error while adding user in HBase", e);
        } finally {
            IOUtils.closeQuietly(table);
        }
    }
}
