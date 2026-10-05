package org.apache.james.xodus;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.util.CompressBackupUtil;

public class XodusBackupTask implements Task {
    private static final Logger LOGGER = LoggerFactory.getLogger(XodusBackupTask.class);
    public static final TaskType TASK_TYPE = TaskType.of("xodus-backup");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final Instant timestamp;
        private final String backupDir;
        private final String backupFile;
        private final long sizeBytes;

        public AdditionalInformation(Instant timestamp, String backupDir, String backupFile, long sizeBytes) {
            this.timestamp = timestamp;
            this.backupDir = backupDir;
            this.backupFile = backupFile;
            this.sizeBytes = sizeBytes;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }

        public String getBackupDir() {
            return backupDir;
        }

        public String getBackupFile() {
            return backupFile;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }
    }

    private final PersistentEntityStore entityStore;
    private final File backupDir;
    private volatile AdditionalInformation additionalInformation;

    public XodusBackupTask(PersistentEntityStore entityStore, File backupDir) {
        this.entityStore = entityStore;
        this.backupDir = backupDir;
        this.additionalInformation = new AdditionalInformation(Clock.systemUTC().instant(), backupDir.getAbsolutePath(), null, 0);
    }

    @Override
    public Result run() {
        try {
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                throw new IllegalStateException("Cannot create backup directory: " + backupDir.getAbsolutePath());
            }

            LOGGER.info("Executing Xodus online hot backup task into {}", backupDir.getAbsolutePath());
            File backupFile = CompressBackupUtil.backup(entityStore, backupDir, "xodus-backup-", true);
            LOGGER.info("Xodus backup completed: {} ({} bytes)", backupFile.getAbsolutePath(), backupFile.length());

            this.additionalInformation = new AdditionalInformation(
                Clock.systemUTC().instant(),
                backupDir.getAbsolutePath(),
                backupFile.getAbsolutePath(),
                backupFile.length());

            return Result.COMPLETED;
        } catch (Exception e) {
            LOGGER.error("Xodus backup task failed", e);
            return Result.PARTIAL;
        }
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.ofNullable(additionalInformation);
    }
}
