package org.totalgrid.reef.api.sapi.client.rpc.impl

/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
import org.totalgrid.reef.api.proto.ProcessStatus.StatusSnapshot
import org.totalgrid.reef.api.japi.client.rpc.impl.builder.ApplicationConfigBuilders
import org.totalgrid.reef.api.sapi.client.rpc.impl.framework.HasAnnotatedOperations

import org.totalgrid.reef.api.sapi.client.rpc.ApplicationService
import org.totalgrid.reef.api.japi.client.NodeSettings

trait ApplicationServiceImpl extends HasAnnotatedOperations with ApplicationService {

  override def registerApplication(config: NodeSettings, instanceName: String, capabilities: List[String]) = {
    ops.operation("Failed registering application") {
      _.put(ApplicationConfigBuilders.makeProto(config, instanceName, capabilities.toList)).map(_.one)
    }
  }
  override def sendHeartbeat(statusSnapshot: StatusSnapshot) =
    ops.operation("Heartbeat failed")(_.put(statusSnapshot).map(_.one))

}