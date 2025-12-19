package com.eventstore.domain.exceptions

class UserAlreadyExistsException(email: String) :
    RuntimeException("User with email '$email' already exists")

class UserNotFoundException(id: String) :
    RuntimeException("User with id '$id' not found")

class InvalidCredentialsException :
    RuntimeException("Invalid credentials")

