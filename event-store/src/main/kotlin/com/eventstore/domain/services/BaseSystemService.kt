package com.eventstore.domain.services

import com.eventstore.Config

/**
 * Base class for system services that require system event publishing capabilities.
 * Multi-tenant support is always enabled.
 */
abstract class BaseSystemService(
    protected val config: Config,
    protected val eventPublisher: SystemEventPublisher,
)
