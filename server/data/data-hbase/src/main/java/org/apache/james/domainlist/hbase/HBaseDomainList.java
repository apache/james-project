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

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.hbase.def.HDomainList;
import org.apache.james.domainlist.lib.AbstractDomainList;
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
    public HBaseDomainList(DNSService dns) {
        super(dns);
    }

    @Override
    protected boolean containsDomainInternal(Domain domain) throws DomainListException {
        HTableInterface table = null;
        try {
            table = TablePool.getInstance().getDomainlistTable();
            Get get = new Get(Bytes.toBytes(domain.asString()));
            Result result = table.get(get);
            if (!result.isEmpty()) {
                return true;
            }
        } catch (IOException e) {
            log.error("Error while counting domains from HBase", e);
            throw new DomainListException("Error while counting domains from HBase", e);
        } finally {
            IOUtils.closeQuietly(table);
        }
        return false;
    }

    @Override
    public void addDomain(Domain domain) throws DomainListException {
        if (containsDomain(domain)) {
            throw new DomainListException(domain.name() + " already exists.");
        }
        HTableInterface table = null;
        try {
            table = TablePool.getInstance().getDomainlistTable();
            Put put = new Put(Bytes.toBytes(domain.asString()));
            put.add(HDomainList.COLUMN_FAMILY_NAME, HDomainList.COLUMN.DOMAIN, null);
            table.put(put);
            table.flushCommits();
        } catch (IOException e) {
            log.error("Error while adding domain in HBase", e);
            throw new DomainListException("Error while adding domain in HBase", e);
        } finally {
            IOUtils.closeQuietly(table);
        }
    }

    @Override
    public void removeDomain(Domain domain) throws DomainListException {
        HTableInterface table = null;
        try {
            table = TablePool.getInstance().getDomainlistTable();
            Delete delete = new Delete(Bytes.toBytes(domain.asString()));
            table.delete(delete);
            table.flushCommits();
        } catch (IOException e) {
            log.error("Error while deleting user from HBase", e);
            throw new DomainListException("Error while deleting domain from HBase", e);
        } finally {
            IOUtils.closeQuietly(table);
        }
    }

    @Override
    protected List<Domain> getDomainListInternal() throws DomainListException {
        List<Domain> list = new ArrayList<>();
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
                list.add(Domain.of(Bytes.toString(result.getRow())));
            }
        } catch (IOException e) {
            log.error("Error while counting domains from HBase", e);
            throw new DomainListException("Error while counting domains from HBase", e);
        } finally {
            IOUtils.closeQuietly(resultScanner);
            IOUtils.closeQuietly(table);
        }
        return list;
    }
}
