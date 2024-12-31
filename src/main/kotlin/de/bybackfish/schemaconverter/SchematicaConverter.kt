package de.bybackfish.schemaconverter

import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class SchematicaConverter : JavaPlugin() {

    companion object {
        const val SCHEMATICA_EXTENSION = "schem"
    }

    override fun onEnable() {
        getCommand("bulkconvert")?.apply {
            val command = BulkConvertCommand()

            setExecutor(command)
            tabCompleter = command
        }

        dataFolder.mkdirs()
    }

    inner class BulkConvertCommand : CommandExecutor, TabCompleter {
        private val scope = CoroutineScope(IO)

        override fun onCommand(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>?
        ): Boolean {
            if (args == null || args.size < 2) {
                sender.sendMessage("Usage: /bulkconvert <folder> <current format> [new format] [destination]")
                return true
            }

            val folderName = args[0]
            val currentFormatName = args[1]
            val newFormatName = if (args.size > 2) args[2] else null
            val destination = if (args.size > 3) args[3] else "converted"

            val currentFormat = findFormatByName(currentFormatName)
            val newFormat = if (newFormatName == null) BuiltInClipboardFormat.FAST_V3 else findFormatByName(newFormatName)

            when {
                currentFormat == null -> {
                    sender.sendMessage(Component.text("Unknown current format: $currentFormatName", NamedTextColor.RED))
                    return true
                }
                newFormat == null -> {
                    sender.sendMessage(Component.text("Unknown new format: $newFormatName", NamedTextColor.RED))
                    return true
                }
            }

            val startTime = System.currentTimeMillis()
            val folder = dataFolder.resolve(folderName)

            if (!folder.exists()) {
                sender.sendMessage(
                    Component.text()
                        .append(Component.text("Folder $folderName does not exist", NamedTextColor.RED))
                        .appendNewline()
                        .append(Component.text("Make sure it's located in plugins/SchematicaConverter/", NamedTextColor.RED))
                        .build()
                )

                return true
            }

            val outputFolder = folder.resolve(destination)
            outputFolder.exists() || outputFolder.mkdirs()

            logger.info("Converting files in ${folder.absolutePath}")

            val files = folder.listFiles()?.filter { it.isFile && it.extension == SCHEMATICA_EXTENSION } ?: emptyList()

            if (files.isEmpty()) {
                sender.sendMessage(Component.text("No files found to convert in $folderName", NamedTextColor.RED))
                return true
            }

            sender.sendMessage(Component.text("Converting ${files.size} files...", NamedTextColor.YELLOW))

            scope.launch {
                convertFiles(sender, files, currentFormat, newFormat, outputFolder)

                val summary = Component.text()
                    .append(Component.text("Done! Conversion complete:", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text("• Converted: ", NamedTextColor.GRAY))
                    .append(Component.text("${files.size} files", NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("• From: ", NamedTextColor.GRAY))
                    .append(Component.text(currentFormat.name, NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("• To: ", NamedTextColor.GRAY))
                    .append(Component.text(newFormat.name, NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("• Time: ", NamedTextColor.GRAY))
                    .append(Component.text("${System.currentTimeMillis() - startTime}ms", NamedTextColor.WHITE))
                    .build()

                sender.sendMessage(summary)
            }

            return true
        }

        override fun onTabComplete(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>?
        ): List<String?>? {
            return when (args?.size) {
                1 -> dataFolder.list()?.toList() ?: emptyList()
                2, 3 -> BuiltInClipboardFormat.entries.map { it.name }
                else -> emptyList()
            }
        }
    }

    suspend fun convertFiles(sender: CommandSender, files: List<File>, currentFormat: BuiltInClipboardFormat, newFormat: BuiltInClipboardFormat, outputFolder: File) {
        files.forEach { file ->
            withContext(IO) {
                try {
                    val outputFile = outputFolder.resolve(file.name)

                    currentFormat.load(file).use { clipboard ->
                        clipboard.save(outputFile, newFormat)
                    }
                    sender.sendMessage(
                        Component.text("Converted ${file.name}", NamedTextColor.GREEN)
                    )
                } catch (e: Exception) {
                    sender.sendMessage(
                        Component.text("Failed to convert ${file.name}: ${e.message}", NamedTextColor.RED)
                    )
                }
            }
        }
    }

    fun findFormatByName(name: String): BuiltInClipboardFormat? {
        return BuiltInClipboardFormat.entries.firstOrNull {
            it.name.equals(name, ignoreCase = true) || it.aliases.any { alias -> alias.equals(name, ignoreCase = true) }
        }
    }
}