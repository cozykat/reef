/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the GNU Affero General Public License
 * Version 3.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/agpl.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.reef.simulator.default

import org.totalgrid.reef.util.Logging
import org.totalgrid.reef.executor.Executor
import org.totalgrid.reef.api.protocol.api.Publisher
import org.totalgrid.reef.api.proto.{ Measurements, SimMapping }
import org.totalgrid.reef.api.protocol.simulator.{ SimulatorPlugin, SimulatorPluginFactory }

object DefaultSimulatorFactory extends SimulatorPluginFactory with Logging {

  def name = "benchmark"

  def getSimLevel(endpointName: String, config: SimMapping.SimulatorMapping): Int = 0

  def createSimulator(endpointName: String, executor: Executor, publisher: Publisher[Measurements.MeasurementBatch], config: SimMapping.SimulatorMapping): SimulatorPlugin = {
    val sim = new DefaultSimulator(endpointName, publisher, config, executor, this)
    sim.start()
    sim
  }

  def destroySimulator(plugin: SimulatorPlugin): Unit = plugin match {
    case sim: DefaultSimulator => sim.stop()
    case _ => logger.error("Cannot destroy unknown simulator type: " + plugin)
  }
}