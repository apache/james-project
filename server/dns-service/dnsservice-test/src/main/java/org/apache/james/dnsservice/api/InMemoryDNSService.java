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

package org.apache.james.dnsservice.api;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.net.InetAddresses;

public class InMemoryDNSService implements DNSService {

    private Map<String,DNSRecord> records;

    public InMemoryDNSService() {
        records = Maps.newHashMap();
        records.put("0.0.0.0", dnsRecordFor(InetAddresses.forString("0.0.0.0")));
        records.put("127.0.0.1", dnsRecordFor(InetAddresses.forString("127.0.0.1")));
    }

    private DNSRecord dnsRecordFor(InetAddress addresses) {
        Collection<String> emptyList = ImmutableList.of();
        return dnsRecordFor(emptyList, emptyList, ImmutableList.of(addresses));
    }

    private DNSRecord dnsRecordFor(Collection<String> mxRecords, Collection<String> txtRecords, List<InetAddress> addresses) {
        return new DNSRecord(addresses, mxRecords, txtRecords);
    }

    public InMemoryDNSService registerRecord(String hostname, InetAddress address, String mxRecord) {
        Collection<String> emptyTxtRecords = ImmutableList.of();
        registerRecord(hostname, ImmutableList.of(address), ImmutableList.of(mxRecord), emptyTxtRecords);
        return this;
    }

    public InMemoryDNSService registerMxRecord(String hostname, String ip) throws UnknownHostException {
        InetAddress containerIp = InetAddress.getByName(ip);
        registerRecord(hostname, containerIp, hostname);
        return this;
    }

    public InMemoryDNSService registerRecord(String hostname, List<InetAddress> addresses, Collection<String> mxRecords, Collection<String> txtRecords) {
        records.put(hostname, dnsRecordFor(mxRecords, txtRecords, addresses));
        return this;
    }

    public void dropRecord(String hostname) {
        records.remove(hostname);
    }

    @Override
    public Collection<String> findMXRecords(String hostname) throws TemporaryResolutionException {
        try {
            return hostRecord(hostname).mxRecords;
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Collection<String> findTXTRecords(String hostname) {
        try {
            return hostRecord(hostname).txtRecords;
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<InetAddress> getAllByName(String host) throws UnknownHostException {
        return hostRecord(host).addresses;
    }

    @Override
    public InetAddress getByName(String host) throws UnknownHostException {
        return hostRecord(host).addresses.get(0);
    }

    private DNSRecord hostRecord(final String host) throws UnknownHostException {
        Predicate<? super Entry<String, DNSRecord>> filterByKey = entry -> entry.getKey().equals(host);
        return getDNSEntry(filterByKey).getValue();
    }

    @Override
    public InetAddress getLocalHost() throws UnknownHostException {
        return InetAddress.getLocalHost();
    }

    @Override
    public String getHostName(final InetAddress addr) {
        Predicate<? super Entry<String, DNSRecord>> filterByValue = entry -> entry.getValue().contains(addr);

        try {
            return getDNSEntry(filterByValue).getKey();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private Entry<String, DNSRecord> getDNSEntry(Predicate<? super Entry<String, DNSRecord>> filter) throws UnknownHostException {
        return records.entrySet().stream()
            .filter(filter)
            .findFirst()
            .orElseThrow(() -> new UnknownHostException());
    }

    public static class DNSRecord {

        final Collection<String> mxRecords;
        final Collection<String> txtRecords;
        private final List<InetAddress> addresses;

        public DNSRecord(List<InetAddress> addresses, Collection<String> mxRecords, Collection<String> txtRecords) {
            this.addresses = addresses;
            this.mxRecords = mxRecords;
            this.txtRecords = txtRecords;
        }

        public boolean contains(InetAddress address) {
            return addresses.contains(address);
        }
    }
}