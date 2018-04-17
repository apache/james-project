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
package org.apache.james.rrt.hbase;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.james.core.Domain;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.hbase.def.HRecipientRewriteTable;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.system.hbase.TablePool;
import org.apache.james.util.OptionalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Splitter;

/**
 * Implementation of the RecipientRewriteTable for a HBase persistence.
 */
public class HBaseRecipientRewriteTable extends AbstractRecipientRewriteTable {

    private static final Logger log = LoggerFactory.getLogger(HBaseRecipientRewriteTable.class.getName());
    private static final String ROW_SEPARATOR = "@";
    private static final String COLUMN_SEPARATOR = ";";

    @Override
    public void addMapping(String user, Domain domain, Mapping mapping) throws RecipientRewriteTableException {
        String fixedUser = getFixedUser(user);
        Domain fixedDomain = getFixedDomain(domain);
        Mappings map = getUserDomainMappings(fixedUser, fixedDomain);
        if (!map.isEmpty()) {
            Mappings updatedMappings = MappingsImpl.from(map).add(mapping).build();
            doUpdateMapping(fixedUser, fixedDomain, updatedMappings.serialize());
        } else {
            doAddMapping(fixedUser, fixedDomain, mapping.asString());
        }
    }

    @Override
    public Mappings getUserDomainMappings(String user, Domain domain) throws
            RecipientRewriteTableException {
        HTableInterface table = null;
        Mappings list = MappingsImpl.empty();
        try {
            table = TablePool.getInstance().getRecipientRewriteTable();
            // Optimize this to only make one call.
            return feedUserDomainMappingsList(table, user, domain, list);
        } catch (IOException e) {
            log.error("Error while getting user domain mapping in HBase", e);
            throw new RecipientRewriteTableException("Error while getting user domain mapping in HBase", e);
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

    private Mappings feedUserDomainMappingsList(HTableInterface table, String user, Domain domain, Mappings list) throws
            IOException {
        Get get = new Get(Bytes.toBytes(getRowKey(user, domain)));
        Result result = table.get(get);
        List<KeyValue> keyValues = result.getColumn(HRecipientRewriteTable.COLUMN_FAMILY_NAME,
                                                    HRecipientRewriteTable.COLUMN.MAPPING);
        if (keyValues.size() > 0) {
            return MappingsImpl.from(list)
                    .addAll(MappingsImpl.fromRawString(Bytes.toString(keyValues.get(0).getValue()))).build();
        }
        return list;
    }

    @Override
    protected Map<String, Mappings> getAllMappingsInternal() throws RecipientRewriteTableException {
        HTableInterface table = null;
        ResultScanner resultScanner = null;
        Map<String, Mappings> map = new HashMap<>();
        try {
            table = TablePool.getInstance().getRecipientRewriteTable();
            Scan scan = new Scan();
            scan.addFamily(HRecipientRewriteTable.COLUMN_FAMILY_NAME);
            scan.setCaching(table.getConfiguration().getInt("hbase.client.scanner.caching", 1) * 2);
            resultScanner = table.getScanner(scan);
            Result result;
            while ((result = resultScanner.next()) != null) {
                List<KeyValue> keyValues = result.list();
                if (keyValues != null) {
                    for (KeyValue keyValue : keyValues) {
                        String email = Bytes.toString(keyValue.getRow());
                        Mappings mappings = 
                                MappingsImpl.from(
                                    Optional.ofNullable(
                                        map.get(email))
                                        .orElse(MappingsImpl.empty()))
                                .addAll(Splitter.on(COLUMN_SEPARATOR).split(Bytes.toString(keyValue.getValue())))
                                .build();
                        map.put(email, mappings);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error while getting all mapping from HBase", e);
            throw new RecipientRewriteTableException("Error while getting all mappings from HBase", e);
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
        return map;
    }

    @Override
    protected Mappings mapAddress(String user, Domain domain) throws RecipientRewriteTableException {
        return getApplicableMappingRow(user, domain)
            .map(MappingsImpl::fromRawString)
            .orElse(MappingsImpl.empty());
    }

    private Optional<String> getApplicableMappingRow(String user, Domain domain) throws RecipientRewriteTableException {
        HTableInterface table = null;
        try {
            table = TablePool.getInstance().getRecipientRewriteTable();
            HTableInterface tableCopy = table;
            return OptionalUtils.orSuppliers(
                Throwing.supplier(() -> Optional.ofNullable(getMapping(tableCopy, user, domain))).sneakyThrow(),
                Throwing.supplier(() -> Optional.ofNullable(getMapping(tableCopy, WILDCARD, domain))).sneakyThrow(),
                Throwing.supplier(() -> Optional.ofNullable(getMapping(tableCopy, user, Domains.WILDCARD))).sneakyThrow());
        } catch (IOException e) {
            log.error("Error while mapping address in HBase", e);
            throw new RecipientRewriteTableException("Error while mapping address in HBase", e);
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

    private String getMapping(HTableInterface table, String user, Domain domain) throws IOException {
        Get get = new Get(Bytes.toBytes(getRowKey(user, domain)));
        Result result = table.get(get);
        List<KeyValue> keyValues = result.getColumn(HRecipientRewriteTable.COLUMN_FAMILY_NAME,
                                                    HRecipientRewriteTable.COLUMN.MAPPING);
        if (keyValues.size() > 0) {
            return Bytes.toString(keyValues.get(0).getValue());
        }
        return null;
    }

    @Override
    public void removeMapping(String user, Domain domain, Mapping mapping) throws
            RecipientRewriteTableException {
        String fixedUser = getFixedUser(user);
        Domain fixedDomain = getFixedDomain(domain);
        Mappings map = getUserDomainMappings(fixedUser, fixedDomain);
        if (map.size() > 1) {
            Mappings updatedMappings = map.remove(mapping);
            doUpdateMapping(fixedUser, fixedDomain, updatedMappings.serialize());
        } else {
            doRemoveMapping(fixedUser, fixedDomain);
        }
    }

    /**
     * Update the mapping for the given user and domain.
     * For HBase, this is simply achieved delegating
     * the work to the doAddMapping method.
     * 
     * @param user the user
     * @param domain the domain
     * @param mapping the mapping
     * @throws RecipientRewriteTableException
     */
    private void doUpdateMapping(String user, Domain domain, String mapping) throws RecipientRewriteTableException {
        doAddMapping(user, domain, mapping);
    }

    /**
     * Remove a mapping for the given user and domain.
     * 
     * @param user the user
     * @param domain the domain
     * @throws RecipientRewriteTableException
     */
    private void doRemoveMapping(String user, Domain domain) throws RecipientRewriteTableException {
        HTableInterface table = null;
        try {
            table = TablePool.getInstance().getRecipientRewriteTable();
            Delete delete = new Delete(Bytes.toBytes(getRowKey(user, domain)));
            table.delete(delete);
            table.flushCommits();
        } catch (IOException e) {
            log.error("Error while removing mapping from HBase", e);
            throw new RecipientRewriteTableException("Error while removing mapping from HBase", e);
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
     * Add mapping for given user and domain
     * 
     * @param user the user
     * @param domain the domain
     * @param mapping the mapping
     * @throws RecipientRewriteTableException
     */
    private void doAddMapping(String user, Domain domain, String mapping) throws RecipientRewriteTableException {
        HTableInterface table = null;
        try {
            table = TablePool.getInstance().getRecipientRewriteTable();
            Put put = new Put(Bytes.toBytes(getRowKey(user, domain)));
            put.add(HRecipientRewriteTable.COLUMN_FAMILY_NAME, HRecipientRewriteTable.COLUMN.MAPPING, Bytes.toBytes(
                    mapping));
            table.put(put);
            table.flushCommits();
        } catch (IOException e) {
            log.error("Error while adding mapping in HBase", e);
            throw new RecipientRewriteTableException("Error while adding mapping in HBase", e);
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
     * Constructs a Key based on the user and domain.
     * 
     * @param user
     * @param domain
     * @return the key
     */
    private String getRowKey(String user, Domain domain) {
        return user + ROW_SEPARATOR + domain.asString();
    }
}
