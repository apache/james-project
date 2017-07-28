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
package org.apache.james.domainlist.hbase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.hbase.def.HDomainList;
import org.apache.james.domainlist.lib.AbstractDomainList;
import org.apache.james.domainlist.lib.EnvDetector;
import org.apache.james.system.hbase.TablePool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the DomainList for a HBase persistence.
 */
public class HBaseDomainList extends AbstractDomainList {

    /**
     * The Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(HBaseDomainList.class.getName());

    @Inject
    public HBaseDomainList(DNSService dns, EnvDetector envDetector) {
        super(dns, envDetector);
    }

    /**
     * @see org.apache.james.domainlist.api.DomainList#containsDomain(String)
     */
    @Override
    protected boolean containsDomainInternal(String domain) throws DomainListException {
        HTableInterface table = null;
        try {
            table = TablePool.getInstance().getDomainlistTable();
            Get get = new Get(Bytes.toBytes(domain.toLowerCase(Locale.US)));
            Result result = table.get(get);
            if (!result.isEmpty()) {
                return true;
            }
        } catch (IOException e) {
            log.error("Error while counting domains from HBase", e);
            throw new DomainListException("Error while counting domains from HBase", e);
        } finally {
            if (table != null) {
                try {
                    table.close();
                } catch (IOException e) {
                    // Do nothing, we can't get access to the HBaseSchema.
                }
            }
        }
        return false;
    }

    /**
     * @see org.apache.james.domainlist.api.DomainList#addDomain(String)
     */
    @Override
    public void addDomain(String domain) throws DomainListException {
        String lowerCasedDomain = domain.toLowerCase(Locale.US);
        if (containsDomain(lowerCasedDomain)) {
            throw new DomainListException(lowerCasedDomain + " already exists.");
        }
        HTableInterface table = null;
        try {
            table = TablePool.getInstance().getDomainlistTable();
            Put put = new Put(Bytes.toBytes(lowerCasedDomain));
            put.add(HDomainList.COLUMN_FAMILY_NAME, HDomainList.COLUMN.DOMAIN, null);
            table.put(put);
            table.flushCommits();
        } catch (IOException e) {
            log.error("Error while adding domain in HBase", e);
            throw new DomainListException("Error while adding domain in HBase", e);
        } finally {
            if (table != null) {
                try {
                    table.close();
                } catch (IOException e) {
                    // Do nothing, we can't get access to the HBaseSchema.
                }
            }
        }
    }

    @Override
    public void removeDomain(String domain) throws DomainListException {
        HTableInterface table = null;
        try {
            table = TablePool.getInstance().getDomainlistTable();
            Delete delete = new Delete(Bytes.toBytes(domain.toLowerCase(Locale.US)));
            table.delete(delete);
            table.flushCommits();
        } catch (IOException e) {
            log.error("Error while deleting user from HBase", e);
            throw new DomainListException("Error while deleting domain from HBase", e);
        } finally {
            if (table != null) {
                try {
                    table.close();
                } catch (IOException e) {
                    // Do nothing, we can't get access to the HBaseSchema.
                }
            }
        }
    }

    /**
     * @see org.apache.james.domainlist.lib.AbstractDomainList#getDomainListInternal()
     */
    @Override
    protected List<String> getDomainListInternal() throws DomainListException {
        List<String> list = new ArrayList<String>();
        HTableInterface table = null;
        ResultScanner resultScanner = null;
        try {
            table = TablePool.getInstance().getDomainlistTable();
            Scan scan = new Scan();
            scan.addFamily(HDomainList.COLUMN_FAMILY_NAME);
            scan.setCaching(table.getConfiguration().getInt("hbase.client.scanner.caching", 1) * 2);
            resultScanner = table.getScanner(scan);
            Result result;
            while ((result = resultScanner.next()) != null) {
                list.add(Bytes.toString(result.getRow()));
            }
        } catch (IOException e) {
            log.error("Error while counting domains from HBase", e);
            throw new DomainListException("Error while counting domains from HBase", e);
        } finally {
            if (resultScanner != null) {
                resultScanner.close();
            }
            if (table != null) {
                try {
                    table.close();
                } catch (IOException e) {
                    // Do nothing, we can't get access to the HBaseSchema.
                }
            }
        }
        return list;
    }
}
