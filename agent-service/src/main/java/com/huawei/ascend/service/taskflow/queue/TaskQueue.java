package com.huawei.ascend.service.taskflow.queue;

import java.util.List;
import java.util.Optional;

public interface TaskQueue<T> {

    String queueId();

    boolean offer(T value);

    Optional<T> poll();

    Optional<T> peek();

    List<T> snapshot();

    int size();
}
