package org.apache.james.mpt.imapmailbox.hbase;

import org.apache.james.mpt.imapmailbox.AbstractMailboxTest;
import org.apache.onami.test.annotation.GuiceModules;

@GuiceModules({ HBaseMailboxTestModule.class })
public class HBaseMailboxTest extends AbstractMailboxTest {

}
