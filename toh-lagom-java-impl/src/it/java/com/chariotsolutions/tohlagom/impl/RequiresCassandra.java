package com.chariotsolutions.tohlagom.impl;

import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
//import org.scalatest.TagAnnotation;

//@TagAnnotation
@Retention(RUNTIME)
@Target({METHOD, TYPE})
public @interface RequiresCassandra {
}
