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

/**
 * @author amit.kumar
 *
 */
class SearchOrderByHandlerTest extends SearchTestBase {
  test("Test json comparison and order by in SearchHandler") {
    val searchOBHandler = SearchOrderByHandler(true, "id")
    val json1 = parseJsonFromFile("./src/test/resources/testDocument.json")
    val json2 = parseJsonFromFile("./src/test/resources/testDocument1.json")
    assertResult(true)(searchOBHandler.compare(json1, json2))
    assertResult(false)(searchOBHandler.compare(json2, json1))
    assertResult(false)(searchOBHandler.compare(json1, json1))
    assertResult(List(json1, json2))(searchOBHandler.orderDocuments(List(json2, json1)))
  }
}