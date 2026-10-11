package dev.pulse.catalog.adapter.outbound.persistence

import dev.pulse.catalog.application.port.outbound.TransactionRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class SpringTransactionRunner(private val template: TransactionTemplate) : TransactionRunner {
    @Suppress("UNCHECKED_CAST")
    override fun <T> inTransaction(block: () -> T): T = template.execute { block() } as T
}
