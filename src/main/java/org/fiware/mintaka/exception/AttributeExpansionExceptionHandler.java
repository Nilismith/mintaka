package org.fiware.mintaka.exception;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;

/**
 * Handler to map errors on attribute extension to matching responses
 */
@Produces
@Singleton
@Requires(classes = {StringExpansionException.class, ExceptionHandler.class})
@Slf4j
public class AttributeExpansionExceptionHandler implements ExceptionHandler<StringExpansionException, HttpResponse<String>> {

	@Override
	public HttpResponse<String> handle(HttpRequest request, StringExpansionException exception) {
		log.warn("Was not able to expand attributes.");
		log.debug("Expansion error: ", exception);
		return HttpResponse.badRequest(String.format("Was not able to expand requested attributes with the given context. Error: %s", exception.getMessage()));
	}
}
