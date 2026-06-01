package com.huawei.ascend.service.taskflow.queue;

public final class QueueFactory {

    private QueueFactory() {
    }

    public static <T> TaskQueue<T> inMemoryQueue(String queueId) {
        return new InMemoryTaskQueue<>(queueId);
    }
}
