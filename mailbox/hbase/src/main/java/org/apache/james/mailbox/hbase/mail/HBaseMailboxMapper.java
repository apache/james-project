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
package org.apache.james.mailbox.hbase.mail;

import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOXES_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_MESSAGE_COUNT;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_NAME;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_NAMESPACE;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_USER;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES_META_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_INTERNALDATE;
import static org.apache.james.mailbox.hbase.HBaseUtils.mailboxFromResult;
import static org.apache.james.mailbox.hbase.HBaseUtils.toPut;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.BinaryPrefixComparator;
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.filter.SubstringComparator;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.IOUtils;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.hbase.HBaseId;
import org.apache.james.mailbox.hbase.HBaseNonTransactionalMapper;
import org.apache.james.mailbox.hbase.mail.model.HBaseMailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;

/**
 * Data access management for mailbox.
 *
 */
public class HBaseMailboxMapper extends HBaseNonTransactionalMapper implements MailboxMapper<HBaseId> {

    /**
     * Link to the HBase Configuration object and specific mailbox names
     */
    private final Configuration conf;
    
    public HBaseMailboxMapper(Configuration conf) {
        this.conf = conf;
    }
    
    @Override
    public Mailbox<HBaseId> findMailboxByPath(MailboxPath mailboxPath) throws MailboxException, MailboxNotFoundException {
        HTable mailboxes = null;
        ResultScanner scanner = null;
        try {
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            
            Scan scan = new Scan();
            scan.addFamily(MAILBOX_CF);
            scan.setCaching(mailboxes.getScannerCaching() * 2);
            scan.setMaxVersions(1);

            /*
             * Filters is ORDERED. Passing the parameters in the right order
             * might improve performance: passing the user first means that the
             * other filters will not be tested if the mailbox does not belong
             * to the passed user.
             */
            FilterList filters = new FilterList(FilterList.Operator.MUST_PASS_ALL);
            
            if (mailboxPath.getUser() != null) {
                SingleColumnValueFilter userFilter = new SingleColumnValueFilter(MAILBOX_CF, MAILBOX_USER, CompareOp.EQUAL, Bytes.toBytes(mailboxPath.getUser()));
                filters.addFilter(userFilter);
            }
            SingleColumnValueFilter nameFilter = new SingleColumnValueFilter(MAILBOX_CF, MAILBOX_NAME, CompareOp.EQUAL, Bytes.toBytes(mailboxPath.getName()));
            filters.addFilter(nameFilter);
            SingleColumnValueFilter namespaceFilter = new SingleColumnValueFilter(MAILBOX_CF, MAILBOX_NAMESPACE, CompareOp.EQUAL, Bytes.toBytes(mailboxPath.getNamespace()));
            filters.addFilter(namespaceFilter);
            
            scan.setFilter(filters);
            scanner = mailboxes.getScanner(scan);
            Result result = scanner.next();
            
            if (result == null) {
                throw new MailboxNotFoundException(mailboxPath);
            }
            return mailboxFromResult(result);
        } catch (IOException e) {
            throw new MailboxException("Search of mailbox " + mailboxPath + " failed", e);
        } finally {
            scanner.close();
            if (mailboxes != null) {
                try {
                    mailboxes.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing table " + mailboxes, ex);
                }
            }
        }
    }
    
    @Override
    public List<Mailbox<HBaseId>> findMailboxWithPathLike(MailboxPath mailboxPath) throws MailboxException {
        HTable mailboxes = null;
        ResultScanner scanner = null;
        try {
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            
            Scan scan = new Scan();
            scan.addFamily(MAILBOX_CF);
            scan.setCaching(mailboxes.getScannerCaching() * 2);
            scan.setMaxVersions(1);
            
            FilterList filters = new FilterList(FilterList.Operator.MUST_PASS_ALL);
            
            if (mailboxPath.getUser() != null) {
                SingleColumnValueFilter userFilter = new SingleColumnValueFilter(MAILBOX_CF, MAILBOX_USER, CompareOp.EQUAL, Bytes.toBytes(mailboxPath.getUser()));
                filters.addFilter(userFilter);
            }
            SubstringComparator pathComparator;
            String mboxName = mailboxPath.getName();
            /*
             * TODO: use a RegExFiler
             */
            if (mboxName.length() >= 1) {
                if (mboxName.charAt(mboxName.length() - 1) == '%') {
                    mboxName = mboxName.substring(0, mboxName.length() - 1);
                }
            }
            if (mboxName.length() >= 1) {
                if (mboxName.charAt(0) == '%') {
                    mboxName = mboxName.substring(1);
                }
            }
            pathComparator = new SubstringComparator(mboxName);
            SingleColumnValueFilter nameFilter = new SingleColumnValueFilter(MAILBOX_CF, MAILBOX_NAME, CompareOp.EQUAL, pathComparator);
            filters.addFilter(nameFilter);
            SingleColumnValueFilter namespaceFilter = new SingleColumnValueFilter(MAILBOX_CF, MAILBOX_NAMESPACE, CompareOp.EQUAL, Bytes.toBytes(mailboxPath.getNamespace()));
            filters.addFilter(namespaceFilter);
            
            scan.setFilter(filters);
            scanner = mailboxes.getScanner(scan);
            
            List<Mailbox<HBaseId>> mailboxList = new ArrayList<Mailbox<HBaseId>>();
            
            for (Result result : scanner) {
                mailboxList.add(mailboxFromResult(result));
            }
            return mailboxList;
        } catch (IOException e) {
            throw new MailboxException("Search of mailbox " + mailboxPath + " failed", e);
        } finally {
            scanner.close();
            if (mailboxes != null) {
                try {
                    mailboxes.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing table " + mailboxes, ex);
                }
            }
        }
    }
    
    @Override
    public List<Mailbox<HBaseId>> list() throws MailboxException {
        HTable mailboxes = null;
        ResultScanner scanner = null;
        //TODO: possible performance isssues, we are creating an object from all the rows in HBase mailbox table
        try {
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            Scan scan = new Scan();
            scan.addFamily(MAILBOX_CF);
            scan.setCaching(mailboxes.getScannerCaching() * 2);
            scan.setMaxVersions(1);
            scanner = mailboxes.getScanner(scan);
            List<Mailbox<HBaseId>> mailboxList = new ArrayList<Mailbox<HBaseId>>();
            
            Result result;
            while ((result = scanner.next()) != null) {
                Mailbox<HBaseId> mlbx = mailboxFromResult(result);
                mailboxList.add(mlbx);
            }
            return mailboxList;
        } catch (IOException ex) {
            throw new MailboxException("HBase IOException in list()", ex);
        } finally {
            scanner.close();
            if (mailboxes != null) {
                try {
                    mailboxes.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing table " + mailboxes, ex);
                }
            }
        }
    }
    
    @Override
    public void endRequest() {
    }
    
    @Override
    public void save(Mailbox<HBaseId> mlbx) throws MailboxException {
        //TODO: maybe switch to checkAndPut for transactions
        HTable mailboxes = null;
        try {
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            /*
             * cast to HBaseMailbox to access lastuid and ModSeq
             */
            Put put = toPut((HBaseMailbox) mlbx);
            mailboxes.put(put);
        } catch (IOException ex) {
            throw new MailboxException("IOExeption", ex);
        } finally {
            if (mailboxes != null) {
                try {
                    mailboxes.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing table " + mailboxes, ex);
                }
            }
        }
    }
    
    @Override
    public void delete(Mailbox<HBaseId> mlbx) throws MailboxException {
        //TODO: maybe switch to checkAndDelete
        HTable mailboxes = null;
        try {
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            //TODO: delete all maessages from this mailbox
            Delete delete = new Delete(mlbx.getMailboxId().toBytes());
            mailboxes.delete(delete);
        } catch (IOException ex) {
            throw new MailboxException("IOException in HBase cluster during delete()", ex);
        } finally {
            if (mailboxes != null) {
                try {
                    mailboxes.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing table " + mailboxes, ex);
                }
            }
        }
    }
    
    @Override
    public boolean hasChildren(final Mailbox<HBaseId> mailbox, final char c) throws MailboxException, MailboxNotFoundException {
        HTable mailboxes = null;
        ResultScanner scanner = null;
        try {
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            
            Scan scan = new Scan();
            scan.addFamily(MAILBOX_CF);
            scan.setCaching(mailboxes.getScannerCaching() * 2);
            scan.setMaxVersions(1);
            
            FilterList filters = new FilterList(FilterList.Operator.MUST_PASS_ALL);
            
            if (mailbox.getUser() != null) {
                SingleColumnValueFilter userFilter = new SingleColumnValueFilter(MAILBOX_CF, MAILBOX_USER, CompareOp.EQUAL, Bytes.toBytes(mailbox.getUser()));
                filters.addFilter(userFilter);
            }
            SingleColumnValueFilter nameFilter = new SingleColumnValueFilter(MAILBOX_CF,
                    MAILBOX_NAME,
                    CompareOp.EQUAL,
                    new BinaryPrefixComparator(Bytes.toBytes(mailbox.getName() + c)));
            filters.addFilter(nameFilter);
            SingleColumnValueFilter namespaceFilter = new SingleColumnValueFilter(MAILBOX_CF, MAILBOX_NAMESPACE, CompareOp.EQUAL, Bytes.toBytes(mailbox.getNamespace()));
            filters.addFilter(namespaceFilter);
            
            scan.setFilter(filters);
            scanner = mailboxes.getScanner(scan);
            try {
                if (scanner.next() != null) {
                    return true;
                }
            } catch (IOException e) {
                throw new MailboxNotFoundException("hasChildren() " + mailbox.getName());
            }
            return false;
        } catch (IOException e) {
            throw new MailboxException("Search of mailbox " + mailbox + " failed", e);
        } finally {
            scanner.close();
            if (mailboxes != null) {
                try {
                    mailboxes.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing table " + mailboxes, ex);
                }
            }
        }
    }
    
    public void deleteAllMemberships() {
        HTable messages = null;
        HTable mailboxes = null;
        ResultScanner scanner = null;
        try {
            messages = new HTable(conf, MESSAGES_TABLE);
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            Scan scan = new Scan();
            scan.setMaxVersions(1);
            scan.addColumn(MESSAGES_META_CF, MESSAGE_INTERNALDATE);
            scanner = messages.getScanner(scan);
            Result result;
            List<Delete> deletes = new ArrayList<Delete>();
            while ((result = scanner.next()) != null) {
                deletes.add(new Delete(result.getRow()));
            }
            long totalDeletes = deletes.size();
            messages.delete(deletes);
            if (deletes.size() > 0) {
                //TODO: what shoul we do if not all messages are deleted?
                System.out.println("Just " + deletes.size() + " out of " + totalDeletes + " messages have been deleted");
                //throw new RuntimeException("Just " + deletes.size() + " out of " + totalDeletes + " messages have been deleted");
            }
            List<Put> puts = new ArrayList<Put>();
            scan = new Scan();
            scan.setMaxVersions(1);
            scan.addColumn(MAILBOX_CF, MAILBOX_MESSAGE_COUNT);
            IOUtils.cleanup(null, scanner);
            scanner = mailboxes.getScanner(scan);
            Put put = null;
            while ((result = scanner.next()) != null) {
                put = new Put(result.getRow());
                put.add(MAILBOX_CF, MAILBOX_MESSAGE_COUNT, Bytes.toBytes(0L));
                puts.add(new Put());
            }
        } catch (IOException e) {
            throw new RuntimeException("Error deleting MESSAGES table ", e);
        } finally {
            IOUtils.cleanup(null, scanner, messages, mailboxes);
        }
    }
    
    public void deleteAllMailboxes() {
        HTable mailboxes = null;
        ResultScanner scanner = null;
        try {
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            Scan scan = new Scan();
            scan.setMaxVersions(1);
            scan.addColumn(MAILBOX_CF, MAILBOX_NAME);
            scanner = mailboxes.getScanner(scan);
            Result result;
            List<Delete> deletes = new ArrayList<Delete>();
            while ((result = scanner.next()) != null) {
                deletes.add(new Delete(result.getRow()));
            }
            mailboxes.delete(deletes);
        } catch (IOException ex) {
            throw new RuntimeException("IOException deleting mailboxes", ex);
        } finally {
            IOUtils.cleanup(null, scanner, mailboxes);
        }
    }

    @Override
    public void updateACL(Mailbox<HBaseId> mailbox, MailboxACL.MailboxACLCommand mailboxACLCommand) throws MailboxException {
        mailbox.setACL(mailbox.getACL().apply(mailboxACLCommand));
    }
}
