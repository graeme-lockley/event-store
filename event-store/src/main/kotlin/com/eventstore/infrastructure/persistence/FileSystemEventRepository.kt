package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.exceptions.EventStorageException
import com.eventstore.domain.ports.outbound.EventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.PriorityQueue

data class EventFile(
    val id: String,
    val timestamp: String,
    val type: String,
    val payload: Map<String, Any>
)

class FileSystemEventRepository(
    private val dataDir: Path,
    private val objectMapper: ObjectMapper
) : EventRepository {
    private val logger = LoggerFactory.getLogger(FileSystemEventRepository::class.java)

    init {
        // Ensure data directory exists
        try {
            Files.createDirectories(dataDir)
        } catch (e: Exception) {
            throw EventStorageException("Failed to create data directory: ${dataDir}", e)
        }
    }

    private fun getEventFilePath(topic: String, eventId: EventId, timestamp: Instant): Path {
        val date = timestamp.atZone(java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        val sequence = eventId.sequence
        val group = String.format("%04d", sequence / 1000)

        val fileName = "${eventId.value}.json"
        return dataDir.resolve(topic).resolve(date).resolve(group).resolve(fileName)
    }

    override suspend fun storeEvent(
        topic: String,
        type: String,
        payload: Map<String, Any>,
        eventId: EventId,
        timestamp: Instant
    ): Event {
        return withContext(Dispatchers.IO) {
            try {
                val event = Event(eventId, timestamp, type, payload)
                val filePath = getEventFilePath(topic, eventId, timestamp)

                // Ensure directory exists
                Files.createDirectories(filePath.parent)

                // Write event to file
                val eventFile = EventFile(
                    id = eventId.value,
                    timestamp = timestamp.toString(),
                    type = type,
                    payload = payload
                )
                val json = objectMapper.writeValueAsString(eventFile)
                Files.writeString(filePath, json)

                event
            } catch (e: EventStorageException) {
                throw e
            } catch (e: Exception) {
                throw EventStorageException("Failed to store event ${eventId.value} for topic $topic", e)
            }
        }
    }

    override suspend fun storeEvents(events: List<Event>): List<Event> {
        if (events.isEmpty()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            val storedEvents = mutableListOf<Event>()
            val storedPaths = mutableListOf<Path>()

            try {
                for (event in events) {
                    val filePath = getEventFilePath(event.id.topic, event.id, event.timestamp)

                    // Ensure directory exists
                    Files.createDirectories(filePath.parent)

                    // Write event to file
                    val eventFile = EventFile(
                        id = event.id.value,
                        timestamp = event.timestamp.toString(),
                        type = event.type,
                        payload = event.payload
                    )
                    val json = objectMapper.writeValueAsString(eventFile)
                    Files.writeString(filePath, json)

                    storedEvents.add(event)
                    storedPaths.add(filePath)
                }
                storedEvents
            } catch (e: Exception) {
                // Best-effort cleanup: attempt to remove files that were written
                for (path in storedPaths) {
                    try {
                        if (Files.exists(path)) {
                            Files.delete(path)
                        }
                    } catch (cleanupException: Exception) {
                        logger.warn("Failed to cleanup event file ${path} after bulk storage failure: ${cleanupException.message}")
                    }
                }
                throw EventStorageException("Failed to store ${events.size} events (${storedEvents.size} stored before failure)", e)
            }
        }
    }

    override suspend fun getEvent(topic: String, eventId: EventId): Event? {
        return withContext(Dispatchers.IO) {
            try {
                val topicDir = dataDir.resolve(topic)
                if (!Files.exists(topicDir)) {
                    return@withContext null
                }

                // Search for event file by walking the directory structure
                Files.walk(topicDir).use { paths ->
                    paths.filter { it.fileName.toString() == "${eventId.value}.json" }
                        .findFirst()
                        .map { path ->
                            try {
                                val json = Files.readString(path)
                                val eventFile: EventFile = objectMapper.readValue(json)
                                Event(
                                    id = EventId(eventFile.id),
                                    timestamp = Instant.parse(eventFile.timestamp),
                                    type = eventFile.type,
                                    payload = eventFile.payload
                                )
                            } catch (e: Exception) {
                                logger.warn("Failed to read event file ${path}: ${e.message}")
                                null
                            }
                        }
                        .orElse(null)
                }
            } catch (e: Exception) {
                throw EventStorageException("Failed to retrieve event ${eventId.value} for topic $topic", e)
            }
        }
    }

    override suspend fun getEvents(
        topic: String,
        sinceEventId: EventId?,
        date: String?,
        limit: Int?
    ): List<Event> {
        return withContext(Dispatchers.IO) {
            val topicDir = if (date != null) {
                dataDir.resolve(topic).resolve(date)
            } else {
                dataDir.resolve(topic)
            }

            if (!Files.exists(topicDir)) {
                return@withContext emptyList()
            }

            // Use priority queue when limit is specified to maintain only top N events in memory
            // Max heap: comparator reverses order so we keep smallest (earliest) events
            val eventCollection: MutableCollection<Event> = if (limit != null && limit > 0) {
                PriorityQueue(limit + 1) { a, b -> compareEventIds(b.id, a.id) }
            } else {
                mutableListOf()
            }

            Files.walk(topicDir).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                    .forEach { path ->
                        try {
                            val json = Files.readString(path)
                            val eventFile: EventFile = objectMapper.readValue(json)
                            val event = Event(
                                id = EventId(eventFile.id),
                                timestamp = Instant.parse(eventFile.timestamp),
                                type = eventFile.type,
                                payload = eventFile.payload
                            )

                            // Apply filters early
                            if (sinceEventId != null && compareEventIds(event.id, sinceEventId) <= 0) {
                                return@forEach
                            }

                            if (date != null) {
                                val eventDate = event.timestamp.atZone(java.time.ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                                if (eventDate != date) {
                                    return@forEach
                                }
                            }

                            // Add to collection
                            if (eventCollection is PriorityQueue) {
                                eventCollection.add(event)
                                // Keep only the smallest (earliest) N events
                                if (eventCollection.size > limit!!) {
                                    eventCollection.remove() // Remove largest (latest) event
                                }
                            } else {
                                (eventCollection as MutableList).add(event)
                            }
                        } catch (e: Exception) {
                            // Log and skip invalid event files, but don't fail the entire operation
                            logger.warn("Failed to read event file ${path}: ${e.message}", e)
                        }
                    }
            }

            // Convert to sorted list
            val sortedEvents = if (eventCollection is PriorityQueue) {
                // Priority queue is max heap, so reverse to get ascending order
                eventCollection.sortedWith { a, b -> compareEventIds(a.id, b.id) }
            } else {
                (eventCollection as MutableList<Event>).sortedWith { a, b -> compareEventIds(a.id, b.id) }
            }

            sortedEvents
        }
    }

    override suspend fun getLatestEventId(topic: String): EventId? {
        val events = getEvents(topic)
        return events.lastOrNull()?.id
    }

    private fun compareEventIds(id1: EventId, id2: EventId): Int {
        // Compare topic names first
        if (id1.topic != id2.topic) {
            return id1.topic.compareTo(id2.topic)
        }

        // Compare sequence numbers
        return id1.sequence.compareTo(id2.sequence)
    }
}

