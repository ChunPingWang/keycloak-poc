package com.shopmall.membership.infrastructure.web;

import com.shopmall.membership.domain.exception.DuplicateEmailException;
import com.shopmall.membership.domain.exception.DuplicateIdentityException;
import com.shopmall.membership.domain.exception.MemberAlreadySuspendedException;
import com.shopmall.membership.domain.exception.MemberNotFoundException;
import com.shopmall.membership.domain.exception.MemberSuspendedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({DuplicateEmailException.class, DuplicateIdentityException.class})
    ProblemDetail duplicate(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Email 已被註冊");
    }

    @ExceptionHandler(MemberNotFoundException.class)
    ProblemDetail notFound(MemberNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({MemberAlreadySuspendedException.class, MemberSuspendedException.class})
    ProblemDetail conflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
