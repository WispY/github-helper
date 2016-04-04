package com.wispy.githubhelper;

@FunctionalInterface
public interface CheckedFunction<T, R> {

    R apply(T object) throws Exception;

}