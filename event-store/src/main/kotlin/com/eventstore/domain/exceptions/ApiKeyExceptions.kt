package com.eventstore.domain.exceptions

class ApiKeyNotFoundException(id: String) :
    RuntimeException("API key with id '$id' not found")

class ApiKeyAlreadyRevokedException(id: String) :
    RuntimeException("API key with id '$id' is already revoked")

class InvalidApiKeyException(message: String) :
    RuntimeException("Invalid API key: $message")

