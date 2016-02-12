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

package org.apache.james.mpt.smtp.dns;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.TemporaryResolutionException;

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
        return dnsRecordFor(ImmutableList.of(), ImmutableList.of(), addresses);
    }

    private DNSRecord dnsRecordFor(Collection<String> mxRecords, Collection<String> txtRecords, InetAddress... addresses) {
        return new DNSRecord(addresses, mxRecords, txtRecords);
    }

    public void registerRecord(String hostname, InetAddress[] addresses,Collection<String> mxRecords, Collection<String> txtRecords ){
        records.put(hostname, dnsRecordFor(mxRecords, txtRecords, addresses));
    }

    public void dropRecord(String hostname){
        records.remove(hostname);
    }

    @Override
    public Collection<String> findMXRecords(final String hostname) throws TemporaryResolutionException {
        return hostRecord(hostname).mxRecords;
    }

    @Override
    public Collection<String> findTXTRecords(String hostname) {
        return hostRecord(hostname).txtRecords;
    }

    @Override
    public InetAddress[] getAllByName(String host) throws UnknownHostException {
        return hostRecord(host).addresses;
    }

    @Override
    public InetAddress getByName(String host) throws UnknownHostException {
        return hostRecord(host).addresses[0];
    }

    private DNSRecord hostRecord(String host) {
        Predicate<? super Entry<String, DNSRecord>> filterByKey = (entry) -> entry.getKey().equals(host);
        return getDNSEntry(filterByKey).getValue();
    }

    @Override
    public InetAddress getLocalHost() throws UnknownHostException {
        return InetAddress.getLocalHost();
    }

    @Override
    public String getHostName(InetAddress addr) {
        Predicate<? super Entry<String, DNSRecord>> filterByValue = (entry) -> entry.getValue().contains(addr);
        return getDNSEntry(filterByValue).getKey();
    }

    private Entry<String, DNSRecord> getDNSEntry(Predicate<? super Entry<String, DNSRecord>> filter) {
        return records.entrySet().stream()
                .filter(filter)
                .findFirst()
                .get();
    }

    private static class DNSRecord {

        final InetAddress[] addresses;
        final Collection<String> mxRecords;
        final Collection<String> txtRecords;
        private final List<InetAddress> addressList;

        public DNSRecord(InetAddress[] adresses, Collection<String> mxRecords, Collection<String> txtRecords) {
            this.addresses = adresses;
            this.mxRecords = mxRecords;
            this.txtRecords = txtRecords;
            this.addressList = ImmutableList.copyOf(addresses);
        }

        public boolean contains(InetAddress address){
            return addressList.contains(address);
        }
    }
}