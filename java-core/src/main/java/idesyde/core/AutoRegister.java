package idesyde.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation ensures that an (reverse) identification rule or an explorer can be found later by the
 * orchestrator.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AutoRegister {

    Class<? extends Module> value();
}
