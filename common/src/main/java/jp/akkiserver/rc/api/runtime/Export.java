package jp.akkiserver.rc.api.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an operation exported to the guest runtime.
 *
 * @author akki697222
 * @since 0.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Export {
    /**
     * Whether invocation of this operation is asynchronous.
     *
     * @return whether invocation is asynchronous
     */
    boolean async() default false;
}
