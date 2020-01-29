/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.statefun.examples.ridesharing.simulator.simulation.engine;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

@Component
public class Scheduler {

  private static final int THREAD_COUNT = 4;

  private final AtomicBoolean started = new AtomicBoolean(false);

  private final ConcurrentHashMap<String, Task> tasks = new ConcurrentHashMap<>();

  private final Executor eventLoopExecutor =
      Executors.newFixedThreadPool(THREAD_COUNT, DaemonThreadFactory.INSTANCE);

  private final EventLoop[] eventLoops = new EventLoop[THREAD_COUNT];

  public boolean start() {
    if (!started.compareAndSet(false, true)) {
      return false;
    }
    for (int i = 0; i < THREAD_COUNT; i++) {
      EventLoop eventLoop = new EventLoop(tasks);
      eventLoops[i] = eventLoop;
      eventLoopExecutor.execute(eventLoop);
    }
    return true;
  }

  public void add(Simulatee simulatee) {
    Objects.requireNonNull(simulatee);

    Task task = new Task(simulatee);
    task.enqueue(LifecycleMessages.initialization());

    tasks.put(task.id(), task);

    eventLoopFor(task).addToReadySet(task);
  }

  public void enqueueTaskMessage(String simulateeId, Object message) {
    Objects.requireNonNull(simulateeId);
    Objects.requireNonNull(message);

    final @Nullable Task task = tasks.get(simulateeId);
    if (task == null) {
      return;
    }
    task.enqueue(message);
    eventLoopFor(task).addToReadySet(task);
  }

  private EventLoop eventLoopFor(Task task) {
    return eventLoops[partition(task)];
  }

  private static int partition(Task task) {
    Objects.requireNonNull(task);
    return Math.abs(task.id().hashCode()) % THREAD_COUNT;
  }
}
