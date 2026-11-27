package com.nep.tsuki.media.constants;

public final class MediaRabbitConstants {
    private MediaRabbitConstants() {
    }

    public static final String JOBS_EXCHANGE = "tsuki.media.jobs";
    public static final String IMAGE_PROCESS_QUEUE = "tsuki.media.image.process";
    public static final String IMAGE_PROCESS_ROUTING_KEY = "image.process";
    public static final String IMAGE_LISTENER_CONTAINER_FACTORY = "mediaImageListenerContainerFactory";

}
