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
package org.totalgrid.reef.measurementstore

import org.totalgrid.reef.client.service.proto.Measurements.Measurement

import org.totalgrid.reef.persistence.{ ObjectCache, KeyValue }

/**
 * wraps a MeasurementStore implementation to conform to the ObjectCache interface so we dont have to implement
 * the same logic in two places
 */
class MeasurementStoreToMeasurementCacheAdapter(measStore: MeasurementStore) extends ObjectCache[Measurement] {

  override def put(values: List[KeyValue[Measurement]]): Unit = measStore.set(values.map { kv => kv.value })

  def put(name: String, obj: Measurement) = measStore.set(obj :: Nil)

  def get(name: String): Option[Measurement] = measStore.get(name :: Nil).get(name)

  def delete(name: String) = throw new Exception("Shouldn't delete measurements through the cache")

}