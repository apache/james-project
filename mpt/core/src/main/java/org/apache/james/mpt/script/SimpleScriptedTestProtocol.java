package org.apache.james.mpt.script;

import org.apache.james.mpt.api.HostSystem;

public class SimpleScriptedTestProtocol extends GenericSimpleScriptedTestProtocol<HostSystem, SimpleScriptedTestProtocol> {

    public SimpleScriptedTestProtocol(String scriptDirectory, HostSystem hostSystem) throws Exception {
        super(scriptDirectory, hostSystem);
    }
    
}