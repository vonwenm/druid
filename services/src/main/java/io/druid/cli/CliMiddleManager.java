/*
 * Druid - a distributed column store.
 * Copyright (C) 2012, 2013  Metamarkets Group Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package io.druid.cli;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import com.metamx.common.logger.Logger;
import io.airlift.command.Command;
import io.druid.guice.IndexingServiceFirehoseModule;
import io.druid.guice.IndexingServiceModuleHelper;
import io.druid.guice.IndexingServiceTaskLogsModule;
import io.druid.guice.Jerseys;
import io.druid.guice.JsonConfigProvider;
import io.druid.guice.LazySingleton;
import io.druid.guice.LifecycleModule;
import io.druid.guice.ManageLifecycle;
import io.druid.guice.annotations.Self;
import io.druid.indexing.common.config.TaskConfig;
import io.druid.indexing.overlord.ForkingTaskRunner;
import io.druid.indexing.overlord.TaskRunner;
import io.druid.indexing.worker.Worker;
import io.druid.indexing.worker.WorkerCuratorCoordinator;
import io.druid.indexing.worker.WorkerTaskMonitor;
import io.druid.indexing.worker.config.WorkerConfig;
import io.druid.indexing.worker.http.WorkerResource;
import io.druid.segment.realtime.firehose.ChatHandlerProvider;
import io.druid.server.DruidNode;
import io.druid.server.initialization.JettyServerInitializer;
import org.eclipse.jetty.server.Server;

import java.util.List;

/**
 */
@Command(
    name = "middleManager",
    description = "Runs a Middle Manager, this is a \"task\" node used as part of the remote indexing service."
)
public class CliMiddleManager extends ServerRunnable
{
  private static final Logger log = new Logger(CliMiddleManager.class);

  public CliMiddleManager()
  {
    super(log);
  }

  @Override
  protected List<? extends Module> getModules()
  {
    return ImmutableList.<Module>of(
        new Module()
        {
          @Override
          public void configure(Binder binder)
          {
            binder.bindConstant().annotatedWith(Names.named("serviceName")).to("druid/middlemanager");
            binder.bindConstant().annotatedWith(Names.named("servicePort")).to(8091);

            IndexingServiceModuleHelper.configureTaskRunnerConfigs(binder);

            JsonConfigProvider.bind(binder, "druid.indexer.task", TaskConfig.class);
            JsonConfigProvider.bind(binder, "druid.worker", WorkerConfig.class);

            binder.bind(TaskRunner.class).to(ForkingTaskRunner.class);
            binder.bind(ForkingTaskRunner.class).in(LazySingleton.class);

            binder.bind(ChatHandlerProvider.class).toProvider(Providers.<ChatHandlerProvider>of(null));

            binder.bind(WorkerTaskMonitor.class).in(ManageLifecycle.class);
            binder.bind(WorkerCuratorCoordinator.class).in(ManageLifecycle.class);

            LifecycleModule.register(binder, WorkerTaskMonitor.class);
            binder.bind(JettyServerInitializer.class).toInstance(new MiddleManagerJettyServerInitializer());
            Jerseys.addResource(binder, WorkerResource.class);

            LifecycleModule.register(binder, Server.class);
          }

          @Provides
          @LazySingleton
          public Worker getWorker(@Self DruidNode node, WorkerConfig config)
          {
            return new Worker(
                node.getHostAndPort(),
                config.getIp(),
                config.getCapacity(),
                config.getVersion()
            );
          }
        },
        new IndexingServiceFirehoseModule(),
        new IndexingServiceTaskLogsModule()
    );
  }
}
