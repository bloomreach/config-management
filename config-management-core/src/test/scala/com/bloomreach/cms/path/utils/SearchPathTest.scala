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
package com.bloomreach.cms.path.utils

import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import java.net.URLDecoder

/**
 * @author amit.kumar
 *
 */
class SearchPathTest extends FunSuite {
  
  test("test method getNearestDirectKey for array pattern key") {
    
    val decodedSearchKey = URLDecoder.decode("/merchants/test_merchant/TEST/level1/level2%5Bid%3D%221%22%5D/status/validate_configuration", "UTF-8")
    val searchPath = new SearchPath(decodedSearchKey)
    val directKey = searchPath.getNearestDirectKey()
    val searchClausePairList = searchPath.getListOfSearchClauses()
    val projectionAtEnd = searchPath.extractProjectionKey()
    assertResult("/merchants/test_merchant/TEST/level1/level2")(directKey)
  }
  
  test("test extractProjectionKey method") {
    val decodedSearchKey1 = URLDecoder.decode("/merchants/test_merchant/TEST/level1/level2%5B1%5D", "UTF-8")
    val searchPath1 = new SearchPath(decodedSearchKey1)
    val projectionAtEnd1 = searchPath1.extractProjectionKey()
    assertResult(None)(projectionAtEnd1)
    
    val decodedSearchKey2 = URLDecoder.decode("/merchants/test_merchant/TEST/level1/level2%5B1%5D/", "UTF-8")
    val searchPath2 = new SearchPath(decodedSearchKey2)
    val projectionAtEnd2 = searchPath2.extractProjectionKey()
    assertResult("/")(projectionAtEnd2.get)
    
    val decodedSearchKey3 = URLDecoder.decode("/merchants/test_merchant/TEST/level1/level2%5B1%5D/status", "UTF-8")
    val searchPath3 = new SearchPath(decodedSearchKey3)
    val projectionAtEnd3 = searchPath3.extractProjectionKey()
    assertResult("/status")(projectionAtEnd3.get)
  }
  
  test("test listOfSearchClauses") {
    val decodedSearchKey1 = URLDecoder.decode("/customers/test_merchant/TEST/level1/level2%5Bid%3D%221%22%5D/status/validate_configuration", "UTF-8")
    val decodedSearchKey2 = URLDecoder.decode("/customers/test_merchant/TEST/level1/level2%5B1%5D", "UTF-8")
    val decodedSearchKey3 = URLDecoder.decode("/customers/test_merchant/TEST/level1/level2", "UTF-8")
    
    val searchPath1 = new SearchPath(decodedSearchKey1)
    val searchPath2 = new SearchPath(decodedSearchKey2)
    val searchPath3 = new SearchPath(decodedSearchKey3)
    
    assertResult("""id="1"""")(searchPath1.getListOfSearchClauses()(0).condition)
    assertResult("""/customers/test_merchant/TEST/level1/level2""")(searchPath1.getListOfSearchClauses()(0).from)
    assertResult("/status/validate_configuration")(searchPath1.extractProjectionKey().get)
    
    assertResult("1")(searchPath2.getListOfSearchClauses()(0).condition)
    assertResult("""/customers/test_merchant/TEST/level1/level2""")(searchPath2.getListOfSearchClauses()(0).from)
    assertResult(None)(searchPath2.extractProjectionKey())
    
    assertResult(0)(searchPath3.getListOfSearchClauses().size)
    assertResult("/customers/test_merchant/TEST/level1/level2")(searchPath3.extractProjectionKey().get)
  }
  
  test("test method getNearestDirectKey for simple key") {
    val simpleKey = "/merchants/test_merchant/TEST/level1/level2"
    val searchPathForSimpleKey = new SearchPath(simpleKey)
    val directKeyForSimpleKey = searchPathForSimpleKey.getNearestDirectKey()
    assertResult(simpleKey)(directKeyForSimpleKey)
  }
}