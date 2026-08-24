package dev.mcbookshelf.sniffer.gametest.integration

import dev.mcbookshelf.sniffer.gametest.support.DebugSession
import dev.mcbookshelf.sniffer.gametest.support.assertTrue
import dev.mcbookshelf.sniffer.gametest.support.thenReloadUntil
import dev.mcbookshelf.sniffer.gametest.support.thenWaitMillis
import java.nio.file.Files
import java.nio.file.Path
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.storage.LevelResource

/**
 * `/watch`, which keeps a datapack's functions in step with the files on disk without a full reload.
 *
 * A watched directory is followed as it changes: a new `.mcfunction` file becomes a callable function, an edited one replaces the version already loaded, and a deleted one stops being callable at all.
 * The splice goes straight into the running `ServerFunctionLibrary`, so what changes is what the next `/function` call finds, with nothing else about the server disturbed.
 */
class HotReloadIntegrationGameTest {

    @GameTest(environment = "sniffer_test:hot_reload", maxTicks = MAX_TICKS)
    fun watchedFunctionsAreCreatedModifiedAndDeletedInPlace(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val pack = session.server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK)
        val functionDir = pack.resolve("data").resolve(NAMESPACE).resolve("function")
        val file = functionDir.resolve("$FUNCTION_NAME.mcfunction")

        deleteRecursively(pack)
        Files.createDirectories(functionDir)

        helper.startSequence()
            // The watcher registers one native watch per directory, and a directory created moments earlier can miss events, so let the tree settle before starting it and let the watcher come up before touching any file.
            .thenWaitMillis(SETTLE_MS)
            .thenExecute { session.run("watch start $PACK") }
            .thenWaitMillis(SETTLE_MS)
            // A brand-new file becomes a callable function.
            .thenReloadUntil(session, FUNCTION, MARKER, 1, "The created function should be callable") { attempt -> write(file, 1, attempt) }
            // Editing it replaces the loaded version.
            .thenReloadUntil(session, FUNCTION, MARKER, 2, "The edited function should replace the old one") { attempt -> write(file, 2, attempt) }
            // Deleting it unloads the function: calling it writes nothing at all.
            .thenReloadUntil(session, FUNCTION, MARKER, null, "The deleted function should no longer run") {
                Files.deleteIfExists(file)
            }
            // A file written after the watcher let go is not a change it ever saw, so even a reload asking for everything pending has nothing to splice.
            .thenExecute { session.run("watch stop $PACK") }
            .thenWaitMillis(SETTLE_MS)
            .thenExecute { write(file, AFTER_STOP, 0) }
            .thenWaitMillis(SETTLE_MS)
            .thenExecute {
                session.run("watch reload")
                session.clearStored(MARKER)
                session.run("function $FUNCTION")
                assertTrue(
                    session.stored(MARKER) == null,
                    "A stopped watcher should not pick the file back up, got: ${session.stored(MARKER)}",
                )
                deleteRecursively(pack)
            }
            .thenSucceed()
    }

    @GameTest(environment = "sniffer_test:hot_reload_auto", maxTicks = MAX_TICKS)
    fun autoReloadAppliesAChangeWithoutBeingAsked(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val pack = session.server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(AUTO_PACK)
        val functionDir = pack.resolve("data").resolve(AUTO_NAMESPACE).resolve("function")
        val file = functionDir.resolve("$FUNCTION_NAME.mcfunction")

        deleteRecursively(pack)
        Files.createDirectories(functionDir)

        helper.startSequence()
            .thenWaitMillis(SETTLE_MS)
            .thenExecute {
                session.run("watch start $AUTO_PACK")
                session.run("watch auto true")
            }
            .thenWaitMillis(SETTLE_MS)
            // Nothing here ever runs `watch reload`, so the function becoming callable is the watcher reloading of its own accord.
            .thenReloadUntil(
                session,
                AUTO_FUNCTION,
                AUTO_MARKER,
                1,
                "An auto reloading watcher should apply the change on its own",
                manualReload = false,
            ) { attempt -> write(file, AUTO_MARKER, 1, attempt) }
            .thenExecute {
                // The flag is process wide, so leaving it on would change what every later test is watching.
                session.run("watch auto false")
                session.run("watch stop $AUTO_PACK")
                deleteRecursively(pack)
            }
            .thenSucceed()
    }

    /** Writes the watched function so that it stores [marker], with a trailing comment making every [attempt] a distinct file. */
    private fun write(file: Path, marker: Int, attempt: Int) = write(file, MARKER, marker, attempt)

    private fun write(file: Path, key: String, marker: Int, attempt: Int) {
        Files.writeString(
            file,
            "data modify storage sniffer_test:log $key set value $marker\n# attempt $attempt\n",
        )
    }

    private fun deleteRecursively(root: Path) {
        if (Files.notExists(root)) return
        Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::delete)
    }

    private companion object {
        const val PACK = "hotreload"
        const val NAMESPACE = "hot"
        const val FUNCTION_NAME = "target"
        const val FUNCTION = "$NAMESPACE:$FUNCTION_NAME"
        const val MARKER = "hot_reload"

        /** Written into the function only after the watcher was stopped, so finding it means the stop did not hold. */
        const val AFTER_STOP = 9

        const val AUTO_PACK = "hotreload_auto"
        const val AUTO_NAMESPACE = "hotauto"
        const val AUTO_FUNCTION = "$AUTO_NAMESPACE:$FUNCTION_NAME"
        const val AUTO_MARKER = "hot_reload_auto"

        /** Wall clock settling time for the filesystem and the watcher. */
        const val SETTLE_MS = 500L

        /**
         * The tick budget, which is only a safety net here.
         * What this test actually waits for is a few seconds of wall clock, and a tick of a game test server is not a unit of time:
         * the same wait cost 591 ticks in a full suite run and over 3000 in a run of this class alone, so a budget sized for one machine's tick rate fails on a faster one while nothing is wrong.
         * The number is therefore far above what any passing run needs, and only a real hang ever reaches it.
         */
        const val MAX_TICKS = 1_000_000
    }
}