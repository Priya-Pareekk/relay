package com.relay.executor;

import com.relay.domain.entity.Job;
import com.relay.domain.enums.JobType;
import com.relay.exception.JobExecutionException;

public interface JobExecutor {

    JobType getJobType();

    void execute(Job job) throws JobExecutionException;
}
