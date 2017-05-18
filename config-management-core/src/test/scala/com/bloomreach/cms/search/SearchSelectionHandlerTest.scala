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
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JField
import org.json4s.JsonAST.JArray
import com.bloomreach.cms.redis.CacheUpdater

/**
 * @author amit.kumar
 *
 */
class SearchSelectionHandlerTest extends SearchTestBase {
  
  test("Test checkEquality utility method of SearchSelectionHandler") {
    val ssHandler = SearchSelectionHandler(cacheUpdater, cacheReader, uberTemplate, List(SelectionEntity(FieldMatchingQuery("merchants", "test_merchant"), And)))
    val retreivedValue = JObject(List(JField("merchants", JArray(List(JString("test_merchant"), JString("example_merchant"))))))
    
    val expectedValue1 = JObject(List(JField("merchants", JArray(List(JString("test_merchant"))))))
    assertResult(true)(ssHandler.checkEquality("merchants", "test_merchant", retreivedValue, expectedValue1))
    
    val expectedValue2 = JObject(List(JField("merchants", JString("test_merchant"))))
    assertResult(true)(ssHandler.checkEquality("merchants", "test_merchant", retreivedValue, expectedValue2))
  }
  
  test("Selection of documents") {
    val ssHandler = SearchSelectionHandler(cacheUpdater, cacheReader, uberTemplate, List(SelectionEntity(FieldMatchingQuery("merchants", "test_merchant"), And)))
    val testDocument1 = parseJsonFromFile("./src/test/resources/testDocument.json")
    val testDocument2 = parseJsonFromFile("./src/test/resources/testDocument1.json")
    val searchDoc1 = FullDocument(testDocument1)
    val searchDoc2 = FullDocument(testDocument2)
    val universeOfDocuments = Set(searchDoc1, searchDoc2)
    val selectionQuery = FieldMatchingQuery("config/config1/customer", "\"test_merchant\"")
    val selectedSet = ssHandler.findAllDocumentsForSelection(selectionQuery, universeOfDocuments)
    assertResult(Set(searchDoc1))(selectedSet)
  }
}