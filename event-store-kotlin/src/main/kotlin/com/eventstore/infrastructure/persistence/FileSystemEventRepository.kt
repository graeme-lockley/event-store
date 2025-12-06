package com.eventstore.infrastructure.persistence

import com.eventstore.application.repositories.EventRepository
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter

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

    init {
        // Ensure data directory exists
        Files.createDirectories(dataDir)
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
        }
    }

    override suspend fun getEvent(topic: String, eventId: EventId): Event? {
        return withContext(Dispatchers.IO) {
            val topicDir = dataDir.resolve(topic)
            if (!Files.exists(topicDir)) {
                return@withContext null
            }

            // Search for event file by walking the directory structure
            Files.walk(topicDir)
                .filter { it.fileName.toString() == "${eventId.value}.json" }
                .findFirst()
                .map { path ->
                    val json = Files.readString(path)
                    val eventFile: EventFile = objectMapper.readValue(json)
                    Event(
                        id = EventId(eventFile.id),
                        timestamp = Instant.parse(eventFile.timestamp),
                        type = eventFile.type,
                        payload = eventFile.payload
                    )
                }
                .orElse(null)
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

            val events = mutableListOf<Event>()

            Files.walk(topicDir)
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
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

                        // Apply filters
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

                        events.add(event)
                    } catch (e: Exception) {
                        // Skip invalid event files
                    }
                }

            // Sort by event ID
            events.sortWith { a, b -> compareEventIds(a.id, b.id) }

            // Apply limit
            if (limit != null && limit > 0) {
                events.take(limit).toList()
            } else {
                events.toList()
            }
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

