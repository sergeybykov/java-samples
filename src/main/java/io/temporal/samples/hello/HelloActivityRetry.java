/*
 *  Copyright (c) 2020 Temporal Technologies, Inc. All Rights Reserved
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.samples.hello;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Functions;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;
import java.util.Arrays;

/**
 * Demonstrates activity retries using an exponential backoff algorithm. Requires a local instance
 * of the Temporal service to be running.
 */
public class HelloActivityRetry {

  static final String TASK_QUEUE = "HelloActivityRetry";

  @WorkflowInterface
  public interface GreetingWorkflow {
    @WorkflowMethod
    String getGreeting(String name);
  }

  @ActivityInterface
  public interface GreetingActivities {
    String composeGreeting(String greeting, String name);
  }

  /**
   * GreetingWorkflow implementation that demonstrates activity stub configured with {@link
   * RetryOptions}.
   */
  public static class GreetingRetryWorkflowImpl implements GreetingWorkflow {

    /**
     * To enable activity retry set {@link RetryOptions} on {@link ActivityOptions}. It also works
     * for activities invoked through {@link io.temporal.workflow.Async#function(Functions.Func)}
     * and for child workflows.
     */
    private final GreetingActivities activities =
        Workflow.newActivityStub(
            GreetingActivities.class,
            ActivityOptions.newBuilder()
                .setScheduleToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(
                    RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setDoNotRetry(IllegalArgumentException.class.getName())
                        .build())
                .build());

    @Override
    public String getGreeting(String name) {
      // This is a blocking call that returns only after activity is completed.
      return activities.composeGreeting("Hello", name);
    }
  }

  static class GreetingActivitiesRetryImpl implements GreetingActivities {
    private int callCount;
    private long lastInvocationTime;

    @Override
    public synchronized String composeGreeting(String greeting, String name) {
      if (lastInvocationTime != 0) {
        long timeSinceLastInvocation = System.currentTimeMillis() - lastInvocationTime;
        System.out.print(timeSinceLastInvocation + " milliseconds since last invocation. ");
      }
      lastInvocationTime = System.currentTimeMillis();
      if (++callCount < 4) {
        System.out.println("composeGreeting activity is going to fail");
        throw new IllegalStateException("not yet");
      }
      System.out.println("composeGreeting activity is going to complete");
      return greeting + " " + name + "!";
    }
  }

  public static void main(String[] args) {
    WorkflowClient client =
        HelloSetup.startWorker(
            TASK_QUEUE,
            Arrays.asList(GreetingRetryWorkflowImpl.class),
            Arrays.asList(new GreetingActivitiesRetryImpl()));

    // Get a workflow stub using the same task queue the worker uses.
    WorkflowOptions workflowOptions = WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build();
    GreetingWorkflow workflow = client.newWorkflowStub(GreetingWorkflow.class, workflowOptions);
    // Execute a workflow waiting for it to complete.
    String greeting = workflow.getGreeting("World");
    System.out.println(greeting);
    System.exit(0);
  }
}
