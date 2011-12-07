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
package org.totalgrid.reef.client.sapi.rpc.impl

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.totalgrid.reef.client.service.proto.Model.ConfigFile
import collection.mutable.ArrayBuffer
import org.scalatest.BeforeAndAfter
import org.totalgrid.reef.client.service.proto.Model
import java.util.{ Iterator, List }
import collection.Seq
import com.weiglewilczek.slf4s.Logging
import org.totalgrid.reef.client.sapi.rpc.impl.util.ClientSessionSuite

/**
 * example of the live example documentation/tests we use to make it clear exactly how the system is implementing
 * the queries and handling the responses. Written exclusively with the high level apis.
 */
@RunWith(classOf[JUnitRunner])
class ConfigFileServiceTest
    extends ClientSessionSuite("ConfigFile.xml", "Config Files", <div>
                                                                   <p>
                                                                     Config files are for larger hunks of opaque data for use by external applications. Config files can be related to 0 to many entities.
        Config files can be searched for by name, id or by entities they are related to. Searches can all be filtered by mimeType if the name is
        unknown. Names must be unique system-wide.
                                                                   </p>
                                                                 </div>)
    with ShouldMatchers with Logging with BeforeAndAfter {

  val configFiles = new ArrayBuffer[ConfigFile]

  test("Create config files") {
    recorder.addExplanation("Create a free floating config file", "Config files can be created without being attached to an Entity")
    val cf = client.createConfigFile("Test-Config-File", "text/plain", "Data".getBytes()).await
    registerConfigFile(cf)

    recorder.addExplanation("Config File Data Updates", "Config Files can be updated 'in place'")
    client.updateConfigFile(cf, "New Data".getBytes()).await

    recorder.addExplanation("Config Files must be deleted by id", "")
    client.deleteConfigFile(cf).await
  }

  test("Associate Config File to Entity") {
    val entity = client.getEntityByName("StaticSubstation").await

    recorder.addExplanation("Attach Config File to Entity", "If we specify an entity with the config file they will be associated")
    val cf1 = client.createConfigFile("Test-Entity-Text-File", "text/plain", "Data".getBytes(), entity.getUuid).await
    registerConfigFile(cf1)

    val cf2 = client.createConfigFile("Test-Entity-XML-File", "text/xml", "<Data/>".getBytes(), entity.getUuid).await
    registerConfigFile(cf1)

    recorder.addExplanation("Get Config File by Entity", "We can now search for all config files that are associated to an entity")
    client.getConfigFilesUsedByEntity(entity.getUuid).await

    recorder.addExplanation("Get Config File by Entity and MimeType", "We can also filter by mimeType (with or without Entity)")
    client.getConfigFilesUsedByEntity(entity.getUuid, "text/xml").await

    val entity2 = client.getEntityByName("SimulatedSubstation").await
    recorder.addExplanation("Add Entity as User of Config File",
      "We can attach more than one entity user to a config file, notice the returned file now has both entities as users")
    client.addConfigFileUsedByEntity(cf1, entity2.getUuid).await

    client.deleteConfigFile(cf1).await
    client.deleteConfigFile(cf2).await
  }

  private def convertToScalaSequence(files: List[ConfigFile]): Seq[ConfigFile] =
    {
      val list = new ArrayBuffer[ConfigFile]()
      val iterator: Iterator[ConfigFile] = files.iterator()
      while (iterator.hasNext) {
        list += iterator.next()
      }
      list.toSeq
    }

  override protected def before(fun: => Any) {
    logger.debug("before(): " + configFiles.length)
    configFiles.clear()
  }

  override protected def after(fun: => Any) {
    logger.debug("after(): " + configFiles.length)
    configFiles.foreach(configFile =>
      {
        client.deleteConfigFile(configFile)
      })
    configFiles.clear()
  }

  private def registerConfigFile(configFile: Model.ConfigFile) {
    configFiles += configFile
  }

  private def logProtos(list: Seq[AnyRef]) {
    logger.debug("")
    logger.debug("protos: " + list.length)
    list.foreach(proto => logger.debug("proto(" + proto.getClass.getSimpleName + "): " + proto))
    logger.debug("")
  }

}