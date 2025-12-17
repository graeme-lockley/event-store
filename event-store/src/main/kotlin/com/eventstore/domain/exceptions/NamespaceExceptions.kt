package com.eventstore.domain.exceptions

class NamespaceAlreadyExistsException(namespaceId: String) :
    RuntimeException("Namespace with id '$namespaceId' already exists")

class NamespaceNotFoundException(namespaceId: String) :
    RuntimeException("Namespace with id '$namespaceId' not found")

