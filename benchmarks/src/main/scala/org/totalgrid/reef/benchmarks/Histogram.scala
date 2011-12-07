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

class Histogram(input: List[Any], val parameters: List[Any], val fieldName: String) extends BenchmarkReading {

  def csvName = parameters.mkString(",") + "," + fieldName

  def testOutputNames = List(csvName + ".min", csvName + ".max", csvName + ".average")
  def testOutputs = List(min, max, average)
  def testParameterNames = List("FieldName")
  def testParameters = List(csvName)

  private val longValues: List[Long] = input.map { getLong(_) }
  lazy val min = longValues.min
  lazy val max = longValues.max
  lazy val sum = longValues.sum
  lazy val count = longValues.size
  lazy val average = if (count > 0) sum / count else 0

  private def getLong(obj: Any): Long = obj match {
    case n: Number => n.longValue
    case b: Boolean => if (b) 1L else 0L
    case s: String if s.length != 0 && s != "null" => s.toLong
    case _ => throw new Exception
  }
}

object Histogram {
  def getHistograms(results: List[BenchmarkReading]): List[Histogram] = {
    results.groupBy(_.testParameters).map {
      case (parameters, resultsWithSameParameters) =>
        val readings = resultsWithSameParameters.map { _.testOutputs }.transpose.zipWithIndex

        val fieldNames = resultsWithSameParameters.head.testOutputNames
        readings.map {
          case (rs, index) =>
            new Histogram(rs, parameters, fieldNames(index))
        }.toList
    }.toList.flatten
  }
}