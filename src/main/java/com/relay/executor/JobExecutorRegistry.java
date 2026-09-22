package com.relay.executor;

import com.relay.domain.enums.JobType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class JobExecutorRegistry {

    private final Map<JobType, JobExecutor> executorMap = new EnumMap<>(JobType.class);

    public JobExecutorRegistry(List<JobExecutor> executors) {
        for (JobExecutor executor : executors) {
            executorMap.put(executor.getJobType(), executor);
        }
    }

    public JobExecutor getExecutor(JobType jobType) {
        return Optional.ofNullable(executorMap.get(jobType))
                .orElseThrow(() -> new IllegalArgumentException("No JobExecutor registered for type: " + jobType));
    }
}
