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
package com.excilys.ebi.gatling.core.util

import java.text.Normalizer
import java.util.regex.Pattern

import scala.collection.mutable

import com.excilys.ebi.gatling.core.session.{ EvaluatableString, EvaluatableStringSeq, Session }
import com.excilys.ebi.gatling.core.util.NumberHelper.isNumeric

import grizzled.slf4j.Logging

/**
 * This object groups all utilities for strings
 */
object StringHelper extends Logging {

	val CACHE = mutable.Map.empty[String, EvaluatableString]

	val END_OF_LINE = System.getProperty("line.separator")

	val EL_START = "${"

	val EL_END = "}"

	val EMPTY = ""

	val jdk6Pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")

	val elPattern = """\$\{(.+?)\}""".r
	val elOccurrencePattern = """(.+?)\((.+)\)""".r

	/**
	 * Method that strips all accents from a string
	 */
	def stripAccents(string: String) = {
		val normalized = Normalizer.normalize(string, Normalizer.Form.NFD)
		jdk6Pattern.matcher(normalized).replaceAll(EMPTY);
	}

	def escapeJsQuoteString(s: String) = s.replace("'", "\\\'")

	def attributeAsEvaluatableString(key: String): EvaluatableString = (session: Session) => session.getAttributeAsOption[Any](key)
		.map(_.toString)
		.getOrElse {
			warn("Couldn't resolve session attribute " + key)
			EMPTY
		}

	def attributeAsEvaluatableStringSeq(key: String): EvaluatableStringSeq = (session: Session) => session.getAttributeAsOption[Any](key)
		.map(value => value match {
			case seq: Seq[_] => seq.map(_.toString)
			case mono => List(mono.toString)
		}).getOrElse {
			warn("Couldn't resolve session attribute " + key)
			List(EMPTY)
		}

	def parseEvaluatable(stringToFormat: String): EvaluatableString = {

		def parseStaticParts: Array[String] = elPattern.pattern.split(stringToFormat, -1)

		def parseDynamicParts: Seq[Session => Any] = elPattern
			.findAllIn(stringToFormat)
			.matchData
			.map { data =>
				val elContent = data.group(1)
				elOccurrencePattern.findFirstMatchIn(elContent)
					.map { occurrencePartMatch =>
						val key = occurrencePartMatch.group(1)
						val occurrence = occurrencePartMatch.group(2)
						val occurrenceFunction =
							if (isNumeric(occurrence))
								(session: Session) => Some(occurrence.toInt)
							else
								(session: Session) => session.getAttributeAsOption(occurrence)

						(session: Session) => occurrenceFunction(session)
							.map { resolvedOccurrence =>
								session.getAttributeAsOption[Seq[Any]](key) match {
									case Some(seq) if (seq.isDefinedAt(resolvedOccurrence)) =>
										seq(resolvedOccurrence)
									case _ =>
										warn("Couldn't resolve occurrence " + resolvedOccurrence + " of session multivalued attribute " + key)
										EMPTY
								}

							}.getOrElse {
								warn("Couldn't resolve index session attribute " + occurrence)
								EMPTY
							}

					}.getOrElse {
						val key = data.group(1)
						(session: Session) => session.getAttributeAsOption[Any](key).getOrElse {
							warn("Couldn't resolve session attribute " + key)
							EMPTY
						}
					}
			}.toSeq

		def doParseEvaluatable: EvaluatableString = {
			val dynamicParts = parseDynamicParts

			if (dynamicParts.isEmpty) {
				// no interpolation
				(session: Session) => stringToFormat

			} else {
				val staticParts = parseStaticParts

				val functions = dynamicParts.zip(staticParts)

				(session: Session) => functions
					.foldLeft(new StringBuilder) { (buffer, function) =>
						val (dynamicPart, staticPart) = function
						buffer.append(staticPart).append(dynamicPart(session))
					}.append(staticParts.last)
					.toString
			}
		}

		CACHE.getOrElseUpdate(stringToFormat, doParseEvaluatable)
	}

	def bytes2Hex(bytes: Array[Byte]): String = bytes.foldLeft(new StringBuilder) { (buff, b) =>
		if ((b & 0xff) < 0x10)
			buff.append("0")
		buff.append(java.lang.Long.toString(b & 0xff, 16))
	}.toString

	def trimToOption(string: String) = string.trim match {
		case EMPTY => None
		case string => Some(string)
	}
}
