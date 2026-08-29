package dev.mcbookshelf.sniffer.dap

/**
 * Holds the proxy of the attached editor, which implements every interface of
 * [DapEndpointsRegistry.buildRemoteInterfaces] at once, and hands it out one interface at a time.
 *
 * @author theogiraudet
 */
object DapClient {

    @Volatile
    private var proxy: DapRemote? = null

    /** The proxy as [type], or `null` when no editor is attached. */
    fun <T : Any> of(type: Class<T>): T? {
        val current = proxy ?: return null
        return if (type.isInstance(current)) type.cast(current) else null
    }

    internal fun attach(proxy: DapRemote) {
        this.proxy = proxy
    }

    internal fun detach() {
        proxy = null
    }
}
