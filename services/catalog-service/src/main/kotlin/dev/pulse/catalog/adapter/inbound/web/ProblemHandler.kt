package dev.pulse.catalog.adapter.inbound.web

import dev.pulse.catalog.domain.ListingNotFoundException
import java.time.DateTimeException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/** RFC 9457 problem+json for every error, so clients get one error shape across all services. */
@RestControllerAdvice
class ProblemHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(ListingNotFoundException::class)
    fun notFound(e: ListingNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message ?: "not found")

    /** Domain invariant violations (`require`) and bad zone ids are client errors. */
    @ExceptionHandler(IllegalArgumentException::class, DateTimeException::class)
    fun badRequest(e: RuntimeException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "invalid request")

    /** Invalid state transitions (`check`), e.g. archiving twice. */
    @ExceptionHandler(IllegalStateException::class)
    fun conflict(e: IllegalStateException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.message ?: "conflict")
}
