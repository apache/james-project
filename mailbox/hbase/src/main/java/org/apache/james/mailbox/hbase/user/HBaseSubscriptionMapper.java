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
package org.apache.james.mailbox.hbase.user;

import static org.apache.james.mailbox.hbase.HBaseNames.SUBSCRIPTIONS_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.SUBSCRIPTION_CF;
import static org.apache.james.mailbox.hbase.HBaseUtils.toPut;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.hbase.HBaseNonTransactionalMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.model.Subscription;
import org.apache.james.mailbox.store.user.model.impl.SimpleSubscription;
/**
 * HBase implementation of a {@link SubscriptionMapper}. 
 * I don't know if this class is thread-safe!
 * 
 */
public class HBaseSubscriptionMapper extends HBaseNonTransactionalMapper implements SubscriptionMapper {

    /** Link to the HBase Configuration object and specific mailbox names */
    private final Configuration conf;

    public HBaseSubscriptionMapper(Configuration conf) {
        this.conf = conf;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.user.SubscriptionMapper#findMailboxSubscriptionForUser(java.lang.String, java.lang.String)
     */
    @Override
    public Subscription findMailboxSubscriptionForUser(String user, String mailbox) throws SubscriptionException {
        HTable subscriptions = null;
        try {
            subscriptions = new HTable(conf, SUBSCRIPTIONS_TABLE);
            Subscription subscription = null;
            Get get = new Get(Bytes.toBytes(user));
            get.addFamily(SUBSCRIPTION_CF);
            Result result = subscriptions.get(get);

            if (!result.isEmpty()) {
                if (result.containsColumn(SUBSCRIPTION_CF, Bytes.toBytes(mailbox))) {
                    subscription = new SimpleSubscription(user, mailbox);
                    return subscription;
                }
            }
            return null;
        } catch (IOException e) {
            throw new SubscriptionException(e);
        } finally {
            if (subscriptions != null) {
                try {
                    subscriptions.close();
                } catch (IOException ex) {
                    throw new SubscriptionException(ex);
                }
            }
        }
    }

    /**
     * @throws SubscriptionException 
     * @see org.apache.james.mailbox.store.user.SubscriptionMapper#save(Subscription)
     */
    @Override
    public void save(Subscription subscription) throws SubscriptionException {
        //TODO: maybe switch to checkAndPut
        HTable subscriptions = null;
        try {
            subscriptions = new HTable(conf, SUBSCRIPTIONS_TABLE);
            Put put = toPut(subscription);
            subscriptions.put(put);
        } catch (IOException e) {
            throw new SubscriptionException(e);
        } finally {
            if (subscriptions != null) {
                try {
                    subscriptions.close();
                } catch (IOException ex) {
                    throw new SubscriptionException(ex);
                }
            }
        }
    }

    /**
     * @throws SubscriptionException 
     * @see org.apache.james.mailbox.store.user.SubscriptionMapper#findSubscriptionsForUser(java.lang.String)
     */
    @Override
    public List<Subscription> findSubscriptionsForUser(String user) throws SubscriptionException {
        HTable subscriptions = null;
        try {
            subscriptions = new HTable(conf, SUBSCRIPTIONS_TABLE);
            List<Subscription> subscriptionList = new ArrayList<Subscription>();
            Get get = new Get(Bytes.toBytes(user));
            get.addFamily(SUBSCRIPTION_CF);
            Result result = subscriptions.get(get);
            if (!result.isEmpty()) {
                List<KeyValue> columns = result.list();
                for (KeyValue key : columns) {
                    subscriptionList.add(new SimpleSubscription(user, Bytes.toString(key.getQualifier())));
                }
            }
            return subscriptionList;
        } catch (IOException e) {
            throw new SubscriptionException(e);
        } finally {
            if (subscriptions != null) {
                try {
                    subscriptions.close();
                } catch (IOException ex) {
                    throw new SubscriptionException(ex);
                }
            }
        }
    }

    /**
     * @throws SubscriptionException 
     * @see org.apache.james.mailbox.store.user.SubscriptionMapper#delete(Subscription)
     */
    @Override
    public void delete(Subscription subscription) throws SubscriptionException {
        //TODO: maybe switch to checkAndDelete
        HTable subscriptions = null;
        try {
            subscriptions = new HTable(conf, SUBSCRIPTIONS_TABLE);
            Delete delete = new Delete(Bytes.toBytes(subscription.getUser()));
            delete.deleteColumns(SUBSCRIPTION_CF, Bytes.toBytes(subscription.getMailbox()));
            subscriptions.delete(delete);
            subscriptions.close();
        } catch (IOException e) {
            throw new SubscriptionException(e);
        } finally {
            if (subscriptions != null) {
                try {
                    subscriptions.close();
                } catch (IOException ex) {
                    throw new SubscriptionException(ex);
                }
            }
        }
    }
}
