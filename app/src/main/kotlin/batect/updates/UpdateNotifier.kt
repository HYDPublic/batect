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

package batect.updates

import batect.VersionInfo
import batect.logging.Logger
import batect.ui.Console
import java.time.ZoneOffset
import java.time.ZonedDateTime

class UpdateNotifier(
    private val disableUpdateNotification: Boolean,
    private val updateInfoStorage: UpdateInfoStorage,
    private val updateInfoUpdater: UpdateInfoUpdater,
    private val versionInfo: VersionInfo,
    private val console: Console,
    private val logger: Logger,
    private val timeSource: () -> ZonedDateTime
) {
    constructor(disableUpdateNotification: Boolean, updateInfoStorage: UpdateInfoStorage, updateInfoUpdater: UpdateInfoUpdater, versionInfo: VersionInfo, console: Console, logger: Logger)
        : this(disableUpdateNotification, updateInfoStorage, updateInfoUpdater, versionInfo, console, logger, { ZonedDateTime.now(ZoneOffset.UTC) })

    fun run() {
        if (disableUpdateNotification) {
            return
        }

        val updateInfo = tryToLoadCachedInfo()

        printUpdateNotificationIfRequired(updateInfo)

        if (shouldUpdateCachedInfo(updateInfo)) {
            updateInfoUpdater.updateCachedInfo()
        }
    }

    private fun tryToLoadCachedInfo(): UpdateInfo? {
        try {
            return updateInfoStorage.read()
        } catch (e: Throwable) {
            logger.warn {
                message("Could not load cached update information.")
                exception(e)
            }

            return null
        }
    }

    private fun printUpdateNotificationIfRequired(updateInfo: UpdateInfo?) {
        if (updateInfo == null) {
            return
        }

        if (updateInfo.version > versionInfo.version) {
            console.println("Version ${updateInfo.version} of batect is now available (you have ${versionInfo.version}). For more information, visit ${updateInfo.url}.")
            console.println()
        }
    }

    private fun shouldUpdateCachedInfo(cachedInfo: UpdateInfo?): Boolean {
        if (cachedInfo == null) {
            return true
        }

        val now = timeSource()
        val nextUpdateDue = cachedInfo.lastUpdated.plusHours(36)

        if (nextUpdateDue < now) {
            return true
        }

        return false
    }
}
