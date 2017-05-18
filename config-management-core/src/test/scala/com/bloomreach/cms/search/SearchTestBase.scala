/**
 * Copyright 2016 BloomReach, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.bloomreach.cms.search

import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods
import scala.io.Source
import com.bloomreach.cms.redis.{CacheUpdater, CacheReader}

/**
 * @author amit.kumar
 *
 */
class SearchTestBase extends FunSuite with BeforeAndAfter  {
  
  var uberTemplate: JValue = _
  var cacheUpdater: CacheUpdater = _
  var cacheReader: CacheReader = _
  before {
    uberTemplate = parseJsonFromFile("./src/test/resources/uberTemplate.json")
    cacheUpdater = new CacheUpdater("localhost", 6379)
    cacheReader = new CacheReader("localhost", 6379)
  }
    
  def parseJsonFromFile(filePath: String) : JValue = {
    return JsonMethods.parse(getJsonStringFromFile(filePath))
  }
  
  def getJsonStringFromFile(filePath: String) : String = {
    val source = Source.fromFile(filePath)
    val jsonString = try source.mkString finally source.close()
    return jsonString
  }
}