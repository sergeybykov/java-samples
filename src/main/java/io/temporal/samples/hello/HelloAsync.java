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
import io.temporal.workflow.Async;
import io.temporal.workflow.Functions.Func;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;
import java.util.Arrays;

/**
 * Demonstrates asynchronous activity invocation. Requires a local instance of Temporal server to be
 * running.
 */
public class HelloAsync {

  static final String TASK_QUEUE = "HelloAsync";

  @WorkflowInterface
  public interface GreetingWorkflow {
    @WorkflowMethod
    String getGreeting(String name);
  }

  @ActivityInterface
  public interface GreetingAsyncActivities {
    String composeGreeting(String greeting, String name);
  }

  /**
   * GreetingWorkflow implementation that calls GreetingsActivities#composeGreeting using {@link
   * Async#function(Func)}.
   */
  public static class GreetingAsyncWorkflowImpl implements GreetingWorkflow {

    /**
     * Activity stub implements activity interface and proxies calls to it to Temporal activity
     * invocations. Because activities are reentrant, only a single stub can be used for multiple
     * activity invocations.
     */
    private final GreetingAsyncActivities activities =
        Workflow.newActivityStub(
            GreetingAsyncActivities.class,
            ActivityOptions.newBuilder().setScheduleToCloseTimeout(Duration.ofSeconds(10)).build());

    @Override
    public String getGreeting(String name) {
      // Async.invoke takes method reference and activity parameters and returns Promise.
      Promise<String> hello = Async.function(activities::composeGreeting, "Hello", name);
      Promise<String> bye = Async.function(activities::composeGreeting, "Bye", name);

      // Promise is similar to the Java Future. Promise#get blocks until result is ready.
      return hello.get() + "\n" + bye.get();
    }
  }

  public static void main(String[] args) {

    WorkflowClient client =
        HelloSetup.startWorker(
            TASK_QUEUE,
            Arrays.asList(GreetingAsyncWorkflowImpl.class),
            Arrays.asList(new HelloActivity.GreetingActivitiesImpl()));

    // Start a workflow execution. Usually this is done from another program.\n'
    // Uses task queue from the GreetingWorkflow @WorkflowMethod annotation.
    GreetingWorkflow workflow =
        client.newWorkflowStub(
            GreetingWorkflow.class, WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());
    // Execute a workflow waiting for it to complete.
    String greeting = workflow.getGreeting("World");
    System.out.println(greeting);
    System.exit(0);
  }
}
