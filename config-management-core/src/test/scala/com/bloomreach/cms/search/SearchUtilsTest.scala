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
import org.json4s.JsonAST.{JValue, JNothing}
import org.json4s.jackson.JsonMethods
import scala.io.Source
import com.bloomreach.cms.redis.CacheUpdater
import com.bloomreach.cms.redis.CMSConfig

/**
 * @author amit.kumar
 *
 */
class SearchUtilsTest extends SearchTestBase {
  
  test("Test adding qutoes to string literals in a json") {
    val withoutQuoteJson = "{arr: [1, true, kk, \"ll\", \"merchant is good\"]}"
    val expected = "{\"arr\":[1,true,\"kk\",\"ll\",\"merchant is good\"]}"
    val result = SearchUtils.quoteStringLiteralsInJson(withoutQuoteJson)
    assertResult(expected)(result)
  }
  
  test("String literal check") {
    assertResult(true)(SearchUtils.isValidStringToken("abc123"))
    assertResult(false)(SearchUtils.isValidStringToken("{abc123}"))
    assertResult(false)(SearchUtils.isValidStringToken("123"))
    assertResult(true)(SearchUtils.isValidStringToken("merchant_uk_test1"))
    assertResult(true)(SearchUtils.isValidStringToken("10abcmerchant"))
    assertResult(true)(SearchUtils.isValidStringToken("abc cde"))
    assertResult(true)(SearchUtils.isValidStringToken("(abc)"))
  }
  
  test("Test isKeyAnArrayIndex") {
    assertResult(true)(SearchUtils.isKeyAnArrayIndex("feed[10]"))
    assertResult(false)(SearchUtils.isKeyAnArrayIndex("""feed[feed_id="10"]"""))
    assertResult(false)(SearchUtils.isKeyAnArrayIndex("[ten]"))
    assertResult(false)(SearchUtils.isKeyAnArrayIndex("[10"))
    assertResult(false)(SearchUtils.isKeyAnArrayIndex("10]"))
    assertResult(false)(SearchUtils.isKeyAnArrayIndex("ten"))
    assertResult(false)(SearchUtils.isKeyAnArrayIndex("10"))
    assertResult(false)(SearchUtils.isKeyAnArrayIndex(""))
  }
  
  test("Test isKeyAnArraySearch") {
    assertResult(true)(SearchUtils.isKeyArraySearchQuery("""feed[feed_id="10"]"""))
    assertResult("feed")(SearchUtils.getArrayNameAndQuery("""feed[feed_id="10"]""")._1)
    assertResult(false)(SearchUtils.isKeyArraySearchQuery("feed[feed_id10]"))
    assertResult(false)(SearchUtils.isKeyArraySearchQuery("[10"))
    assertResult(false)(SearchUtils.isKeyArraySearchQuery("10]"))
    assertResult(false)(SearchUtils.isKeyArraySearchQuery("ten"))
    assertResult(false)(SearchUtils.isKeyArraySearchQuery("10"))
    assertResult(false)(SearchUtils.isKeyArraySearchQuery(""))
  }
  
  test("Test getAllLazyDocuments") {
    val doc1 = FullDocument(JNothing)
    val doc2 = LazyDocument(CMSConfig("TEST", "merchants", "test_merchant", "config/account/account-id"), cacheUpdater, cacheReader)
    val doc3 = LazyDocument(CMSConfig("TEST", "merchants", "test_merchant", "config/account/account-id"), cacheUpdater, cacheReader)
    val doc4 = FullDocument(JNothing)
    
    val allLazyDocuments = SearchUtils.getAllLazyDocuments(Set[SearchDocument](doc1, doc2, doc3, doc4))
    assertResult(1)(allLazyDocuments.size)
    assertResult(doc2)(allLazyDocuments.toList(0))
  }
  
  test("Test getUniqueKeyToConfigMapForLazyDocuments") {
    val doc1 = LazyDocument(CMSConfig("TEST", "merchants", "test_merchant", "config/account/account-id"), cacheUpdater, cacheReader)
    val doc2 = LazyDocument(CMSConfig("TEST", "merchants", "test_merchant", "config/account/account-id"), cacheUpdater, cacheReader)
    
    val mapKeyToConfig = SearchUtils.getUniqueKeyToConfigMapForLazyDocuments(Set[LazyDocument](doc1, doc2), "config/account/account-id")
    assertResult(1)(mapKeyToConfig.size)
    assertResult(CMSConfig("TEST", "merchants", "test_merchant", "config/account/account-id"))(mapKeyToConfig("merchants/TEST/config/account/account-id"))
  }
}