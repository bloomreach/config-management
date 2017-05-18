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
import com.bloomreach.cms.redis.CacheUpdater
import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods
import scala.io.Source
import org.json4s.Diff

/**
 * @author amit.kumar
 *
 */
class DocumentSearchPathTest extends SearchTestBase {
    
  test("Test SearchPath parsing") {
    //insert template
    cacheUpdater.addTemplate("merchants", "uberTemplate", uberTemplate)
    
    val fullConfigString = getJsonStringFromFile("./src/test/resources/testDocument.json")
    cacheUpdater.updateRecursively("TEST", "merchants", "test_merchant", "config", fullConfigString)
    
    val searchPath = new DocumentSearchPath("/merchants/test_merchant/TEST/config1", cacheUpdater, cacheReader)
    val allDocuments = searchPath.getDocumentList()
    assertResult(1)(allDocuments.size)
    
    val expectedJsonString = """{
                                  "config1": {
                                    "id": 0,
                                    "default-key": "key1",
                                    "customer": "test_merchant"
                                  }
                                }"""
    
    val expectedJson = JsonMethods.parse(expectedJsonString)
    val actualJson = allDocuments.toList(0).getValueForKey("config1").get
    val Diff(changed, added, deleted) = expectedJson diff actualJson
    
    cacheUpdater.flushCache()
  }
}