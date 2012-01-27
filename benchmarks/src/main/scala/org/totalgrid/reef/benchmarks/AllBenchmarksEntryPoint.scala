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
import org.totalgrid.reef.benchmarks.system._
import org.totalgrid.reef.benchmarks.endpoints.EndpointManagementBenchmark
import org.totalgrid.reef.benchmarks.output.{ DelimitedFileOutput, TeamCityStatisticsXml }
import org.totalgrid.reef.client.service.list.ReefServices
import org.totalgrid.reef.client.sapi.client.rest.Connection
import org.totalgrid.reef.loader.commons.LoaderServicesList

import MeasurementCurrentValueBenchmark._
import java.util.Properties

object AllBenchmarksEntryPoint {

  def main(args: Array[String]) {

    val properties = PropertyReader.readFromFile("benchmarksTarget.cfg")

    val testOptions = PropertyReader.readFromFile("org.totalgrid.reef.benchmarks.cfg")

    val userSettings = new UserSettings(properties)
    val connectionInfo = new AmqpSettings(properties)

    val factory = new ReefFactory(connectionInfo, new ReefServices)

    try {

      val connection = factory.connect()

      runAllTests(connection, userSettings, testOptions)

    } finally {
      factory.terminate()
    }
  }

  def runAllTests(connection: Connection, userSettings: UserSettings, properties: Properties) {
    val client = connection.login(userSettings.getUserName, userSettings.getUserPassword).await
    client.addServicesList(new LoaderServicesList())
    client.setHeaders(client.getHeaders.setTimeout(60000))
    client.setHeaders(client.getHeaders.setResultLimit(10000))
    val services = client.getRpcInterface(classOf[AllScadaService])

    val stream = Some(Console.out)

    var tests = List.empty[BenchmarkTest]

    val options = new SimpleOptionsHandler(properties, "org.totalgrid.reef.benchmarks")

    if (options.getBool("live.enabled")) {
      val c = options.subOptions("live")
      val allPoints = services.getPoints().await.map { _.getName }

      tests ::= new SystemStateBenchmark(c.getInt("requestAttempts"))
      tests ::= new MeasurementStatBenchmark(takeRandom(c.getInt("measStatPoints"), allPoints))
      tests ::= new MeasurementHistoryBenchmark(takeRandom(c.getInt("measStatPoints"), allPoints), c.getIntList("measHistorySizes"), true)
      tests ::= new MeasurementCurrentValueBenchmark(allPoints, testSizes(allPoints.size), c.getInt("measCurrentValueAttempts"))

      if (c.getBool("endpointManagementEnabled")) {
        val protocols = c.getStringList("endpointManagementProtocols")
        val endpointNames = protocols.map { p => services.getEndpoints().await.filter(_.getProtocol == p).map { _.getName } }.flatten

        if (!endpointNames.isEmpty) {
          tests ::= new EndpointManagementBenchmark(endpointNames, c.getInt("endpointManagementCycles"))
        }
      }
    }

    if (options.getBool("measthroughput.enabled")) {
      val c = options.subOptions("measthroughput")

      val concurrentEndpointNames = (1 to c.getInt("numEndpoints")).map { i => "Endpoint" + i }.toList
      val pointsPerEndpoint = c.getInt("numPointsPerEndpoint")
      val pointNames = concurrentEndpointNames.map { ModelCreationUtilities.getPointNames(_, pointsPerEndpoint) }.flatten

      val endpointLoadingWriters = c.getInt("endpointWriters")
      val endpointLoadingBatchSize = c.getInt("endpointBatchSize")

      val totalMeasurements = c.getInt("publishMeasTotal")
      val publishingWriters = c.getIntList("publishMeasWriters")
      val publishingBatchSizes = c.getIntList("publishMeasBatchSizes")

      if (c.getBool("addEndpoints")) {
        tests ::= new EndpointLoaderBenchmark(concurrentEndpointNames, pointsPerEndpoint,
          endpointLoadingWriters, endpointLoadingBatchSize, true, false)
      }
      publishingWriters.foreach { writers =>
        publishingBatchSizes.foreach { batchSize =>
          tests ::= new ConcurrentMeasurementPublishingBenchmark(concurrentEndpointNames, totalMeasurements, writers, batchSize)
        }
      }
      if (c.getBool("measTestReads")) {
        tests ::= new MeasurementStatBenchmark(takeRandom(c.getInt("measStatPoints"), pointNames))
        tests ::= new MeasurementHistoryBenchmark(takeRandom(c.getInt("measHistoryPoints"), pointNames), c.getIntList("measHistorySizes"), false)
        tests ::= new MeasurementCurrentValueBenchmark(pointNames, testSizes(pointNames.size), c.getInt("measCurrentValueAttempts"))
      }
      if (c.getBool("removeEndpoints")) {
        tests ::= new EndpointLoaderBenchmark(concurrentEndpointNames, pointsPerEndpoint,
          endpointLoadingWriters, endpointLoadingBatchSize, false, true)
      }
    }

    val allResults = tests.reverse.map(_.runTest(client, stream)).flatten
    outputResults(allResults)
  }

  def outputResults(allResults: List[BenchmarkReading]) {

    val resultsByFileName = allResults.groupBy(_.csvName)

    val histogramResults = resultsByFileName.map {
      case (csvName, results) =>
        Histogram.getHistograms(csvName, results)
    }.toList.flatten

    BenchmarkUtilities.writeHistogramCsvFiles(histogramResults, "averages")
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

  def takeRandom[A](max: Int, list: List[A]): List[A] = {
    if (list.size < max) list
    else {
      scala.util.Random.shuffle(list).take(max)
    }
  }
}