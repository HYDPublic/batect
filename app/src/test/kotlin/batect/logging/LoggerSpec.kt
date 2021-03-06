/*
   Copyright 2017-2018 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.logging

import batect.testutils.createForEachTest
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object LoggerSpec : Spek({
    describe("a logger") {
        val sink by createForEachTest { mock<LogSink>() }
        val logger by createForEachTest { Logger("some.source", sink) }

        val buildFun: LogMessageBuilder.() -> LogMessageBuilder = { this }

        on("logging a debug-level message") {
            logger.debug(buildFun)

            it("forwards the message to the log sink with the name of the source") {
                verify(sink).write(Severity.Debug, mapOf("@source" to "some.source"), buildFun)
            }
        }

        on("logging a info-level message") {
            logger.info(buildFun)

            it("forwards the message to the log sink with the name of the source") {
                verify(sink).write(Severity.Info, mapOf("@source" to "some.source"), buildFun)
            }
        }

        on("logging a warning-level message") {
            logger.warn(buildFun)

            it("forwards the message to the log sink with the name of the source") {
                verify(sink).write(Severity.Warning, mapOf("@source" to "some.source"), buildFun)
            }
        }

        on("logging a error-level message") {
            logger.error(buildFun)

            it("forwards the message to the log sink with the name of the source") {
                verify(sink).write(Severity.Error, mapOf("@source" to "some.source"), buildFun)
            }
        }
    }
})
