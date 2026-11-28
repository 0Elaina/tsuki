package com.nep.tsuki.file.constants;

public final class FileRabbitConstants {
    private FileRabbitConstants() {
    }

    public static final String JOBS_EXCHANGE = "tsuki.file.jobs";

    public static final String INTERACTIVE_QUEUE = "tsuki.file.interactive";
    public static final String INTERACTIVE_ROUTING_KEY = "file.interactive";
    public static final String INTERACTIVE_LISTENER_CONTAINER_FACTORY = "fileInteractiveListenerContainerFactory";

    public static final String CLEANUP_QUEUE = "tsuki.file.cleanup";
    public static final String CLEANUP_ROUTING_KEY = "file.cleanup";
    public static final String CLEANUP_LISTENER_CONTAINER_FACTORY = "fileCleanupListenerContainerFactory";
}
