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
package org.totalgrid.reef.calc.lib

import eval.{ OperationParser, OperationSource }
import org.totalgrid.reef.client.sapi.client.rest.Client
import org.totalgrid.reef.client.service.proto.OptionalProtos._
import org.totalgrid.reef.client.service.proto.Calculations.{ Calculation }
import net.agileautomata.executor4s.{ Cancelable }
import scala.collection.JavaConversions._
import org.totalgrid.reef.calc.lib.BasicCalculationFactory.MultiCancelable
import org.totalgrid.reef.client.sapi.rpc.AllScadaService

class BasicCalculationFactory(
    rootClient: Client,
    operations: OperationSource,
    metricsSource: CalculationMetricsSource,
    output: OutputPublisher,
    timeSource: TimeSource) extends CalculationFactory {

  def build(config: Calculation): Cancelable = {

    val client = rootClient.spawn()
    val services = client.getRpcInterface(classOf[AllScadaService])

    val expr = config.formula.map(OperationParser.parseFormula(_)).getOrElse {
      throw new Exception("Need formula in calculation config")
    }

    val qualInputStrat = config.triggeringQuality.strategy.map(QualityInputStrategy.build(_)).getOrElse {
      throw new Exception("Need quality input strategy in calculation config")
    }

    val qualOutputStrat = config.qualityOutput.strategy.map(QualityOutputStrategy.build(_)).getOrElse {
      throw new Exception("Need quality output strategy in calculation config")
    }

    val timeOutputStrat = config.timeOutput.strategy.map(TimeStrategy.build(_)).getOrElse {
      throw new Exception("Need time strategy in calculation config")
    }

    val name = config.outputPoint.name.getOrElse {
      throw new Exception("Must have output point name")
    }

    val inputConfigs = config.getCalcInputsList.toList.map(InputBucket.build(_))

    val measSettings = MeasurementSettings(name, config.outputPoint.unit)

    val inputDataManager = new MeasInputManager(services, timeSource)

    val metrics = metricsSource.getCalcMetrics(name)

    val formula = Formula(expr, operations)

    val components = CalculationComponents(formula, qualInputStrat, qualOutputStrat, timeOutputStrat, measSettings)

    val evaluator = new CalculationEvaluator(name, inputDataManager, output, components, metrics)

    val triggerStrat = config.triggering.map(CalculationTriggerStrategy.build(_, client, evaluator.attempt)).getOrElse {
      throw new Exception("Must have triggering config")
    }

    val (eventedTrigger, initiatingTrigger) = triggerStrat match {
      case ev: EventedTriggerStrategy => (Some(ev), None)
      case in: InitiatingTriggerStrategy => (None, Some(in))
    }

    inputDataManager.initialize(inputConfigs, eventedTrigger)

    initiatingTrigger.foreach(_.start())

    new MultiCancelable(List(Some(inputDataManager), initiatingTrigger).flatten)
  }
}

object BasicCalculationFactory {
  class MultiCancelable(list: List[Cancelable]) extends Cancelable {
    def cancel() { list.foreach(_.cancel()) }
  }
}

