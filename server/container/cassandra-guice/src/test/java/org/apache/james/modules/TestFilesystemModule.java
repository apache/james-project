package org.apache.james.modules;

import java.io.File;

import org.apache.james.core.JamesServerResourceLoader;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.modules.CommonServicesModule;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class TestFilesystemModule extends AbstractModule {
    
    private File workingDirectory;

    public TestFilesystemModule(File workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    protected void configure() {
        bind(JamesDirectoriesProvider.class).toInstance(new JamesServerResourceLoader(workingDirectory.getAbsolutePath()));
        bindConstant().annotatedWith(Names.named(CommonServicesModule.CONFIGURATION_PATH)).to(FileSystem.CLASSPATH_PROTOCOL);
    }
    
}