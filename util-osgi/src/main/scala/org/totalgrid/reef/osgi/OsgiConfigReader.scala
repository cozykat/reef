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
package org.totalgrid.reef.osgi

import org.osgi.framework._
import org.osgi.service.cm.ConfigurationAdmin

import com.weiglewilczek.scalamodules._

import java.util.{ Dictionary, Hashtable }
import com.weiglewilczek.slf4s.Logging

class OsgiConfigReader(context: BundleContext, pid: String) extends Logging {

  private val config = context findService withInterface[ConfigurationAdmin] andApply { (service: ConfigurationAdmin) =>
    service.getConfiguration(pid)
  } match {
    case Some(x) => x
    case None => throw new Exception("Unable to find ConfigurationAdmin service")
  }

  private val props: Dictionary[AnyRef, AnyRef] = Option(config.getProperties) match {
    case None => new Hashtable[AnyRef, AnyRef]
    case Some(x: Dictionary[AnyRef, AnyRef]) => x
  }

  def getProperties = props
}

object OsgiConfigReader {
  def apply(context: BundleContext, pid: String) = new OsgiConfigReader(context, pid)

}
