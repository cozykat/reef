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
package org.totalgrid.reef.apienhancer

import com.sun.javadoc.ClassDoc
import java.io.{ PrintStream, File }

/**
 * converts the original apis to use scala lists and return Futures
 */
class ScalaTraits(future: Boolean) extends ApiTransformer with GeneratorFunctions {

  val outputPackage = if (future) ".client.sapi.rpc" else ".client.sapi.sync"

  def make(c: ClassDoc, packageStr: String, outputDir: File, sourceFile: File) {
    getFileStream(packageStr, outputDir, sourceFile, outputPackage, true, c.name) { (stream, javaPackage) =>
      scalaClass(c, stream, javaPackage)
    }
  }

  private def scalaClass(c: ClassDoc, stream: PrintStream, packageName: String) {
    stream.println("package " + packageName)

    addScalaImports(stream, c)

    if (future) stream.println("import org.totalgrid.reef.client.Promise")
    stream.println(commentString(c.getRawCommentText()))
    stream.println("trait " + c.name + "{")

    c.methods.toList.foreach { m =>

      val typAnnotation = typeAnnotation(m, false)

      var msg = "\t" + "def " + m.name + typAnnotation + "("
      msg += m.parameters().toList.map { p =>
        p.name + ": " + scalaTypeString(p.`type`)
      }.mkString(", ")
      msg += ")"

      val basicReturnType = scalaTypeString(m.returnType)

      val returnType = if (isReturnOptional(m)) "Option[" + basicReturnType + "]"
      else basicReturnType

      if (future) msg += ": Promise[" + returnType + "]"
      else msg += ": " + returnType

      stream.println(commentString(m.getRawCommentText()))
      stream.println(msg)
    }

    stream.println("}")
  }

}