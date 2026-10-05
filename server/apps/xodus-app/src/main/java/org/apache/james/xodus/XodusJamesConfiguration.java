package org.apache.james.xodus;

import java.io.File;
import java.util.Optional;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.apache.james.server.core.MissingArgumentException;
import org.apache.james.server.core.configuration.Configuration;

public class XodusJamesConfiguration implements Configuration {

    public static class Builder {
        private Optional<String> rootDirectory = Optional.empty();
        private Optional<ConfigurationPath> configurationPath = Optional.empty();

        public Builder workingDirectory(String path) {
            rootDirectory = Optional.of(path);
            return this;
        }

        public Builder workingDirectory(File file) {
            rootDirectory = Optional.of(file.getAbsolutePath());
            return this;
        }

        public Builder useWorkingDirectoryEnvProperty() {
            rootDirectory = Optional.ofNullable(System.getProperty(WORKING_DIRECTORY));
            if (rootDirectory.isEmpty()) {
                throw new MissingArgumentException("Server needs a working.directory env entry");
            }
            return this;
        }

        public Builder configurationPath(ConfigurationPath path) {
            configurationPath = Optional.of(path);
            return this;
        }

        public Builder configurationFromClasspath() {
            configurationPath = Optional.of(new ConfigurationPath(FileSystem.CLASSPATH_PROTOCOL));
            return this;
        }

        public XodusJamesConfiguration build() {
            String rootDirPath = rootDirectory.orElseThrow(() -> new MissingArgumentException("Server needs a working.directory env entry"));
            ConfigurationPath configPath = configurationPath.orElse(new ConfigurationPath(FileSystem.FILE_PROTOCOL_AND_CONF));
            JamesDirectoriesProvider directoriesProvider = new JamesServerResourceLoader(rootDirPath);

            return new XodusJamesConfiguration(
                configPath,
                directoriesProvider);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final ConfigurationPath configurationPath;
    private final JamesDirectoriesProvider directoriesProvider;

    private XodusJamesConfiguration(ConfigurationPath configurationPath,
                                     JamesDirectoriesProvider directoriesProvider) {
        this.configurationPath = configurationPath;
        this.directoriesProvider = directoriesProvider;
    }

    @Override
    public ConfigurationPath configurationPath() {
        return configurationPath;
    }

    @Override
    public JamesDirectoriesProvider directories() {
        return directoriesProvider;
    }
}
