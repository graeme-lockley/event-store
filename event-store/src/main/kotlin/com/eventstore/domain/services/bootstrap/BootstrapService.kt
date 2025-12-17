package com.eventstore.domain.services.bootstrap

/**
 * Service responsible for bootstrapping the system tenant, management namespace,
 * and reserved system topics on application startup.
 */
interface BootstrapService {
    suspend fun run()
}

