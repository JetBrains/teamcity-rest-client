package org.jetbrains.teamcity.rest;

import retrofit.http.RestMethod;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author nik
 */
@Documented
@Target(METHOD)
@Retention(RUNTIME)
@RestMethod(value = "DELETE", hasBody = true)
public @interface DELETE_WITH_BODY {
  String value();
}
