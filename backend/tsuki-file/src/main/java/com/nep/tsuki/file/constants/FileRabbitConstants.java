package com.nep.tsuki.file.constants;

public final class FileRabbitConstants {
    private FileRabbitConstants() {
    }

    public static final String JOBS_EXCHANGE = "tsuki.file.jobs";
    public static final String IMAGE_PROCESS_QUEUE = "tsuki.file.image.process";
    public static final String IMAGE_PROCESS_ROUTING_KEY = "image.process";
    public static final String IMAGE_LISTENER_CONTAINER_FACTORY = "fileImageListenerContainerFactory";
}
