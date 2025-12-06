package com.eventstore.domain.services

import com.eventstore.domain.ports.outbound.ConsumerRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GetHealthStatusServiceTest {

    @Test
    fun `should return health status with consumer count and dispatchers`() = runTest {
        val consumerRepository = mockk<ConsumerRepository>()
        val runningDispatchers = listOf("topic1", "topic2")
        val service = GetHealthStatusService(consumerRepository) { runningDispatchers }

        coEvery { consumerRepository.count() } returns 5

        val result = service.execute()

        assertEquals("healthy", result.status)
        assertEquals(5, result.consumers)
        assertEquals(runningDispatchers, result.runningDispatchers)
    }

    @Test
    fun `should return zero consumers when none exist`() = runTest {
        val consumerRepository = mockk<ConsumerRepository>()
        val service = GetHealthStatusService(consumerRepository) { emptyList() }

        coEvery { consumerRepository.count() } returns 0

        val result = service.execute()

        assertEquals("healthy", result.status)
        assertEquals(0, result.consumers)
        assertEquals(emptyList(), result.runningDispatchers)
    }

    @Test
    fun `should return empty dispatchers list when none running`() = runTest {
        val consumerRepository = mockk<ConsumerRepository>()
        val service = GetHealthStatusService(consumerRepository) { emptyList() }

        coEvery { consumerRepository.count() } returns 3

        val result = service.execute()

        assertEquals("healthy", result.status)
        assertEquals(3, result.consumers)
        assertEquals(emptyList(), result.runningDispatchers)
    }
}

