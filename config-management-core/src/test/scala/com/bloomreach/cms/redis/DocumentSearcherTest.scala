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
package com.bloomreach.cms.redis

import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.scalatest.Tag
import org.json4s.jackson.JsonMethods
import scala.io.Source
import org.json4s.JsonAST.{JValue, JArray, JNothing, JString, JInt}
import org.json4s.Diff
import com.bloomreach.cms.search.SearchQueryMetaData

/**
 * @author amit.kumar
 *
 */
class DocumentSearcherTest extends FunSuite with BeforeAndAfter {
  
  var cacheUpdater: CacheUpdater = _
  var cacheReader: CacheReader = _
  var cache: Cache = _
  var templateJson: JValue = _
  before {
    cacheUpdater = new CacheUpdater("localhost", 6379)
    cacheReader = new CacheReader("localhost", 6379)
    cache = Cache("localhost", 6379)
    val templateFilePath = "./src/test/resources/uberTemplate.json"
    val templateSource = Source.fromFile(templateFilePath)
    val templateJsonString = try templateSource.mkString finally templateSource.close()
    templateJson = JsonMethods.parse(templateJsonString).asInstanceOf[JValue]
    cache.flushCache()
    setUpDB()
  }
  
  def setUpDB() {
    //Add Template to provide default values
    cache.add(CMSTemplate("merchants", "uberTemplate", templateJson))
    
    //Add dbname on which search will work
    cache.addToSet("dbName", "merchants")
    
    //Add realms on which search will work
    cache.addToSet("dbName/merchants/realms", "TEST")
    
    //Add documentNames on which search will work
    cache.addToSet("dbName/merchants/realm/TEST/documentName", "example_merchant")
  }
  
  after {
    cache.flushCache()
  }
  
  test("Test get Data") {
    //First insert root level key
    val configKey = "config"
    val configJsonString = getConfigJsonString("./src/test/resources/testDocument.json")
    cacheUpdater.updateRecursively("TEST", "merchants", "example_merchant", configKey, configJsonString)
    
    val docSearcher = new DocumentSearcher(cacheUpdater, cacheReader, "merchants", "example_merchant", "TEST")
    val actualData = docSearcher.getData("config1/id").getOrElse("{}")
    val actualDataJson = JsonMethods.parse(actualData) \ "id"
    val expectedDataJson = (JsonMethods.parse(configJsonString) \ "config" \ "config1" \ "id")
    
    val Diff(changed, added, deleted) = expectedDataJson diff actualDataJson
    assertResult(JNothing)(changed)
    assertResult(JNothing)(added)
    assertResult(JNothing)(deleted)
  }
  
  test("Test search Data") {
    //First insert root level key
    val configKey = "config"
    val configJsonString = getConfigJsonString("./src/test/resources/testDocument.json")
    cacheUpdater.updateRecursively("TEST", "merchants", "example_merchant", configKey, configJsonString)
    val docSearcher = new DocumentSearcher(cacheUpdater, cacheReader, "merchants", "example_merchant", "TEST")
    
    val queryString = "collection:TEST"
    val orderByString = ""
    val projectionString = "id,customer"
    val metaData = new SearchQueryMetaData(",", "\\+", ":", "collection", "*")
    val result = docSearcher.searchData(queryString, projectionString, orderByString, metaData)
    val resultJsonElement = (JsonMethods.parse(result)  \ "results" \ "result").asInstanceOf[JArray].arr(0)
    val actualAccountID = (resultJsonElement \ "id").asInstanceOf[JInt].num.toInt
    val actualMerchant = (resultJsonElement \ "customer").asInstanceOf[JString].s
    assertResult(0)(actualAccountID)
    assertResult("test_merchant")(actualMerchant)
  }
  
  def getConfigJsonString(filePath: String): String = {
    val source = Source.fromFile(filePath)
    val jsonString = try source.mkString finally source.close()
    return jsonString
  }
}