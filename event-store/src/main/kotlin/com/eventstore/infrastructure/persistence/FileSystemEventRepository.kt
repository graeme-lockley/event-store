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

    private fun getEventFilePath(topic: String, eventId: EventId): Path {
        val sequence = eventId.sequence
        
        // Hierarchical grouping: millions / ten-thousands / hundreds
        // group1: sequence / 1_000_000 (millions)
        // group2: (sequence / 10_000) % 100 (ten-thousands within million)
        // group3: (sequence / 100) % 100 (hundreds within ten-thousand)
        val group1 = String.format("%03d", sequence / 1_000_000)
        val group2 = String.format("%02d", (sequence / 10_000) % 100)
        val group3 = String.format("%02d", (sequence / 100) % 100)

        val fileName = "${eventId.value}.json"
        return dataDir.resolve(topic)
            .resolve(group1)
            .resolve(group2)
            .resolve(group3)
            .resolve(fileName)
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
                val filePath = getEventFilePath(topic, eventId)

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
                    val filePath = getEventFilePath(event.id.topic, event.id)

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
                val filePath = getEventFilePath(topic, eventId)
                
                if (!Files.exists(filePath)) {
                    return@withContext null
                }
                
                val json = Files.readString(filePath)
                val eventFile: EventFile = objectMapper.readValue(json)
                Event(
                    id = EventId(eventFile.id),
                    timestamp = Instant.parse(eventFile.timestamp),
                    type = eventFile.type,
                    payload = eventFile.payload
                )
            } catch (e: Exception) {
                logger.warn("Failed to read event file for ${eventId.value}: ${e.message}")
                null
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
            val topicDir = dataDir.resolve(topic)

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

            // Calculate starting point if sinceEventId is provided
            val startSequence = sinceEventId?.sequence ?: 0L
            val startGroup1 = (startSequence / 1_000_000).toInt()
            val startGroup2 = ((startSequence / 10_000) % 100).toInt()
            val startGroup3 = ((startSequence / 100) % 100).toInt()

            // Flag to break out of nested loops when limit is reached (for MutableList case)
            var shouldStop = false

            // Traverse directories in sequence order
            Files.list(topicDir).use { group1Dirs ->
                group1Dirs.filter { Files.isDirectory(it) }
                    .sorted() // Natural string sort works for zero-padded numbers
                    .forEach group1Loop@{ group1Dir ->
                        if (shouldStop) return@group1Loop
                        
                        val group1 = group1Dir.fileName.toString().toIntOrNull() ?: return@group1Loop
                        if (group1 < startGroup1) return@group1Loop

                        Files.list(group1Dir).use { group2Dirs ->
                            group2Dirs.filter { Files.isDirectory(it) }
                                .sorted()
                                .forEach group2Loop@{ group2Dir ->
                                    if (shouldStop) return@group2Loop
                                    
                                    val group2 = group2Dir.fileName.toString().toIntOrNull() ?: return@group2Loop
                                    if (group1 == startGroup1 && group2 < startGroup2) return@group2Loop

                                    Files.list(group2Dir).use { group3Dirs ->
                                        group3Dirs.filter { Files.isDirectory(it) }
                                            .sorted()
                                            .forEach group3Loop@{ group3Dir ->
                                                if (shouldStop) return@group3Loop
                                                
                                                val group3 = group3Dir.fileName.toString().toIntOrNull() ?: return@group3Loop
                                                if (group1 == startGroup1 && 
                                                    group2 == startGroup2 && 
                                                    group3 < startGroup3) return@group3Loop

                                                // Read events in this directory
                                                Files.list(group3Dir).use { files ->
                                                    files.filter { 
                                                        Files.isRegularFile(it) && 
                                                        it.fileName.toString().endsWith(".json")
                                                    }
                                                    .sorted() // Sort by filename (which contains sequence)
                                                    .forEach fileLoop@{ path ->
                                                        // Early exit if limit reached and using list
                                                        if (limit != null && 
                                                            limit > 0 && 
                                                            eventCollection !is PriorityQueue &&
                                                            eventCollection.size >= limit) {
                                                            shouldStop = true
                                                            return@fileLoop
                                                        }

                                                        try {
                                                            val json = Files.readString(path)
                                                            val eventFile: EventFile = objectMapper.readValue(json)
                                                            val event = Event(
                                                                id = EventId(eventFile.id),
                                                                timestamp = Instant.parse(eventFile.timestamp),
                                                                type = eventFile.type,
                                                                payload = eventFile.payload
                                                            )

                                                            // Apply filters
                                                            if (sinceEventId != null && 
                                                                compareEventIds(event.id, sinceEventId) <= 0) {
                                                                return@fileLoop
                                                            }

                                                            if (date != null) {
                                                                val eventDate = event.timestamp
                                                                    .atZone(java.time.ZoneId.systemDefault())
                                                                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                                                                if (eventDate != date) {
                                                                    return@fileLoop
                                                                }
                                                            }

                                                            // Add to collection
                                                            if (eventCollection is PriorityQueue) {
                                                                eventCollection.add(event)
                                                                if (eventCollection.size > limit!!) {
                                                                    eventCollection.remove()
                                                                }
                                                            } else {
                                                                (eventCollection as MutableList).add(event)
                                                                // Check if we've reached the limit
                                                                if (limit != null && limit > 0 && eventCollection.size >= limit) {
                                                                    shouldStop = true
                                                                    return@fileLoop
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            logger.warn("Failed to read event file ${path}: ${e.message}", e)
                                                        }
                                                    }
                                                }
                                            }
                                    }
                                }
                        }
                    }
            }

            // Convert to sorted list
            // Optimization: When date is not required, events are already in sequence order
            // from our traversal, so we can skip sorting for MutableList.
            // PriorityQueue always needs sorting since it's a max-heap.
            val sortedEvents = if (eventCollection is PriorityQueue) {
                eventCollection.sortedWith { a, b -> compareEventIds(a.id, b.id) }
            } else if (date != null) {
                // Date filtering may have reordered events, so sort is needed
                (eventCollection as MutableList<Event>).sortedWith { a, b -> compareEventIds(a.id, b.id) }
            } else {
                // No date filter: events are already in sequence order from traversal
                eventCollection.toList()
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

