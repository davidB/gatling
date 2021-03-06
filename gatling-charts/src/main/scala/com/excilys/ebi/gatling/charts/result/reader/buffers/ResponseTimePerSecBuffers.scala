/**
 * Copyright 2011-2012 eBusiness Information, Groupe Excilys (www.excilys.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.excilys.ebi.gatling.charts.result.reader.buffers

import java.util.{ HashMap => JHashMap }

import com.excilys.ebi.gatling.charts.result.reader.ActionRecord
import com.excilys.ebi.gatling.core.result.message.RequestStatus

trait ResponseTimePerSecBuffers extends Buffers {

	val responseTimePerSecBuffers = new JHashMap[BufferKey, RangeBuffer]

	def getResponseTimePerSecBuffers(requestName: Option[String], status: Option[RequestStatus.RequestStatus]): RangeBuffer = getBuffer(computeKey(requestName, status), responseTimePerSecBuffers, () => new RangeBuffer)

	def updateResponseTimePerSecBuffers(record: ActionRecord) {
		getResponseTimePerSecBuffers(Some(record.request), Some(record.status)).update(record.executionStartBucket, record.responseTime)
	}
}