package jp.akkiserver.rc.api.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks integer types as unsigned.
 *
 * <p>Parameter:
 * <pre>{@code
 * @Export
 * public int add(@Unsigned int a, @Unsigned int b) {
 *     return a + b;
 * }
 * }</pre>
 *
 * <p>Return:
 * <pre>{@code
 * public @Unsigned int length(@Unsigned int a) {
 *     ...
 * }
 * }</pre>
 *
 * <p>Type:
 * <pre>{@code
 * List<@Unsigned Integer> u32list = new ArrayList<>();
 * }</pre>
 *
 * @author akki697222
 * @since 0.1.0
 */
@Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Unsigned {
}
