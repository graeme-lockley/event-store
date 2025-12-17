package com.eventstore.domain.exceptions

class TenantAlreadyExistsException(tenantId: String) :
    RuntimeException("Tenant with id '$tenantId' already exists")

class TenantNotFoundException(tenantId: String) :
    RuntimeException("Tenant with id '$tenantId' not found")

