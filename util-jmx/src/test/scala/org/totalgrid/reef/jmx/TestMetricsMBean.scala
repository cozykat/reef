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
package org.totalgrid.reef.jmx
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import management.ManagementFactory
import javax.management.ObjectName

@RunWith(classOf[JUnitRunner])
class TestMetricsMBean extends FunSuite with ShouldMatchers {

  test("Is exposing metrics") {
    val container = MetricsContainer("org.totalgrid.reef.jmx.test", "ReefTestMBean")
    val metric = new MetricValue.CounterMetric
    val metric2 = new MetricValue.CounterMetric
    container.add("SimpleCounter", metric)
    container.add("BiggerCounter", metric2)

    val bean = new MetricsMBean(container)

    metric.update(1)
    metric2.update(5)

    val server = ManagementFactory.getPlatformMBeanServer
    server.registerMBean(bean, bean.getName)

    val name = MBeanUtils.objectName("org.totalgrid.reef.jmx.test", "ReefTestMBean")
    server.isRegistered(name) should equal(true)

    val info = server.getMBeanInfo(name)
    info.getAttributes.length should equal(2)

    val simpleAttr = info.getAttributes.find(_.getName == "SimpleCounter").getOrElse(throw new Exception("Simple counter not found"))
    val biggerAttr = info.getAttributes.find(_.getName == "BiggerCounter").getOrElse(throw new Exception("Bigger counter not found"))

    server.getAttribute(name, "SimpleCounter") should equal(1)
    server.getAttribute(name, "BiggerCounter") should equal(5)
  }
}
