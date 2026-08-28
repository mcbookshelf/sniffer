package dev.mcbookshelf.sniffer.features.source

/**
 * A function, named the way Minecraft names it and located the way the filesystem does.
 *
 * The two always travel together: the location is what the debugger reasons about,
 * the path is what an editor can open, and carrying them as one value is what keeps
 * the name of a function from being paired with the path of another.
 *
 * @property minecraftPath location of the function, as `namespace:path`
 * @property realPath where the function was loaded from, `null` when it could not be resolved
 * @author theogiraudet
 */
data class FunctionIdentity(val minecraftPath: String, val realPath: RealPath?)
