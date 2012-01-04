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
package org.totalgrid.reef.benchmarks

import org.totalgrid.reef.client.sapi.client.factory.ReefFactory
import org.totalgrid.reef.client.settings.util.PropertyReader
import org.totalgrid.reef.client.settings.{ AmqpSettings, UserSettings }
import org.totalgrid.reef.client.sapi.rpc.AllScadaService
import org.totalgrid.reef.benchmarks.measurements._
import org.totalgrid.reef.benchmarks.endpoints.EndpointManagementBenchmark
import org.totalgrid.reef.benchmarks.output.{ DelimitedFileOutput, TeamCityStatisticsXml }
import org.totalgrid.reef.client.service.list.ReefServices
import org.totalgrid.reef.benchmarks.system.SystemStateBenchmark

object AllBenchmarksEntryPoint {
  def main(args: Array[String]) {

    val properties = PropertyReader.readFromFile("benchmarksTarget.cfg")

    val userSettings = new UserSettings(properties)
    val connectionInfo = new AmqpSettings(properties)

    val factory = new ReefFactory(connectionInfo, new ReefServices)

    try {

      val connection = factory.connect()

      val client = connection.login(userSettings.getUserName, userSettings.getUserPassword).await
      client.setHeaders(client.getHeaders.setTimeout(20000))
      client.setHeaders(client.getHeaders.setResultLimit(10000))
      val services = client.getRpcInterface(classOf[AllScadaService])

      val stream = Some(Console.out)

      val endpoints = services.getEndpoints().await.filter(_.getProtocol == "benchmark")

      if (endpoints.isEmpty) throw new FailedBenchmarkException("No endpoints with protocol benchmark on test system")

      val endpointNames = endpoints.map { _.getName }
      val points = endpoints.map { e => services.getPointsBelongingToEndpoint(e.getUuid).await }.flatten

      val tests = List(
        new SystemStateBenchmark(5),
        new MeasurementPublishingBenchmark(endpointNames, 1000, 5, false),
        new MeasurementPublishingBenchmark(endpointNames, 10, 5, false),
        new MeasurementPublishingBenchmark(endpointNames, 10, 5, true),
        new MeasurementStatBenchmark(points),
        new MeasurementHistoryBenchmark(points, List(1, 10, 100, 1000), true),
        new EndpointManagementBenchmark(endpointNames, 5))

      val allResults = tests.map(_.runTest(services, stream)).flatten
      outputResults(allResults)

    } finally {
      factory.terminate()
    }
  }

  def outputResults(allResults: List[BenchmarkReading]) {

    val resultsByFileName = allResults.groupBy(_.csvName)

    val histogramResults = resultsByFileName.map {
      case (csvName, results) =>
        Histogram.getHistograms(results)
    }.toList.flatten

    val teamCity = new TeamCityStatisticsXml("teamcity-info.xml")
    histogramResults.foreach { h =>
      h.outputsWithLabels.foreach { case (label, value) => teamCity.addRow(label, value) }
    }
    teamCity.close()

    resultsByFileName.foreach {
      case (csvName, results) =>
        val output = new DelimitedFileOutput(csvName + ".csv", false)

        output.addRow(results.head.columnNames)
        results.foreach { r => output.addRow(r.values.map { _.toString }) }
        output.close()
    }
  }

}