package io.github.mustafaautomation.payments.api;

import io.github.mustafaautomation.payments.domain.IdempotencyMismatchException;
import io.github.mustafaautomation.payments.domain.InvalidMoneyException;
import io.github.mustafaautomation.payments.domain.PaymentConflictException;
import io.github.mustafaautomation.payments.domain.PaymentNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Maps domain errors to RFC 9457 problem details with a stable "type" per error. */
@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {

    private static ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("urn:problem:" + type));
        return pd;
    }

    @ExceptionHandler(InvalidMoneyException.class)
    ProblemDetail invalidMoney(InvalidMoneyException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-amount", e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraint(ConstraintViolationException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", e.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail notFound(PaymentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "payment-not-found", e.getMessage());
    }

    @ExceptionHandler(PaymentConflictException.class)
    ProblemDetail conflict(PaymentConflictException e) {
        return problem(HttpStatus.CONFLICT, "invalid-state", e.getMessage());
    }

    @ExceptionHandler(IdempotencyMismatchException.class)
    ProblemDetail idempotency(IdempotencyMismatchException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "idempotency-key-reused", e.getMessage());
    }
}
