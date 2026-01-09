package com.eventstore.domain.services

import com.eventstore.Config

/**
 * Base class for system services that require multi-tenant configuration
 * and system event publishing capabilities.
 */
abstract class BaseSystemService(
    protected val config: Config,
    protected val eventPublisher: SystemEventPublisher
) {
    /**
     * Validates that multi-tenant support is enabled.
     * @throws IllegalStateException if multi-tenant support is disabled
     */
    protected fun requireMultiTenantEnabled() {
        if (!config.multiTenantEnabled) {
            throw IllegalStateException("Multi-tenant support is disabled")
        }
    }
}

