package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.dispatch.Output

/** Standard acknowledgement [Output] for actions that produce no data. */
data object Ack : Output
