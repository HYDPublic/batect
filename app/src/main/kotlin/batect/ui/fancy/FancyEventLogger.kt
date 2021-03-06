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

package batect.ui.fancy

import batect.model.events.RunningContainerExitedEvent
import batect.model.events.TaskEvent
import batect.model.steps.CleanUpContainerStep
import batect.model.steps.DeleteTaskNetworkStep
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.RemoveContainerStep
import batect.model.steps.RunContainerStep
import batect.model.steps.TaskStep
import batect.ui.Console
import batect.ui.ConsoleColor
import batect.ui.EventLogger

class FancyEventLogger(
    val console: Console,
    val errorConsole: Console,
    val startupProgressDisplay: StartupProgressDisplay,
    val cleanupProgressDisplay: CleanupProgressDisplay
) : EventLogger() {
    private val lock = Object()
    private var keepUpdatingStartupProgress = true
    private var haveStartedCleanup = false

    override fun onStartingTaskStep(step: TaskStep) {
        synchronized(lock) {
            if (step is DisplayTaskFailureStep) {
                keepUpdatingStartupProgress = false
                displayTaskFailure(step)
                return
            }

            if (step is CleanUpContainerStep || step is RemoveContainerStep || step is DeleteTaskNetworkStep) {
                displayCleanupStatus()
                keepUpdatingStartupProgress = false
                return
            }

            if (keepUpdatingStartupProgress) {
                startupProgressDisplay.onStepStarting(step)
                startupProgressDisplay.print(console)
            }

            if (step is RunContainerStep) {
                console.println()
                keepUpdatingStartupProgress = false
            }
        }
    }

    private fun displayTaskFailure(step: DisplayTaskFailureStep) {
        if (haveStartedCleanup) {
            cleanupProgressDisplay.clear(console)
        } else {
            console.println()
        }

        errorConsole.withColor(ConsoleColor.Red) {
            println(step.message)
            println()
        }

        if (haveStartedCleanup) {
            cleanupProgressDisplay.print(console)
        }
    }

    private fun displayCleanupStatus() {
        if (haveStartedCleanup) {
            cleanupProgressDisplay.clear(console)
        } else {
            console.println()
        }

        cleanupProgressDisplay.print(console)
        haveStartedCleanup = true
    }

    override fun postEvent(event: TaskEvent) {
        synchronized(lock) {
            if (keepUpdatingStartupProgress) {
                startupProgressDisplay.onEventPosted(event)
                startupProgressDisplay.print(console)
            }

            cleanupProgressDisplay.onEventPosted(event)

            if (haveStartedCleanup || event is RunningContainerExitedEvent) {
                displayCleanupStatus()
            }
        }
    }

    override fun onTaskFailed(taskName: String) {
        errorConsole.withColor(ConsoleColor.Red) {
            println()
            print("The task ")
            printBold(taskName)
            println(" failed. See above for details.")
        }
    }

    override fun onTaskStarting(taskName: String) {
        console.withColor(ConsoleColor.White) {
            print("Running ")
            printBold(taskName)
            println("...")
        }
    }
}
