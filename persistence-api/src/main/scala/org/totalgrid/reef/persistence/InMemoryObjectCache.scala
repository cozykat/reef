/**
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Green Energy Corp licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.totalgrid.reef.persistence

import scala.collection.immutable

class InMemoryObjectCache[T] extends ObjectCache[T] {
  private var map = immutable.Map.empty[String, T]
  def getMap = map

  def put(name: String, obj: T) = { map += (name -> obj) }
  def get(name: String): Option[T] = map.get(name)
  def delete(name: String) = { map -= name }
}