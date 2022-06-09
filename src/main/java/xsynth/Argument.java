package xsynth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Argument {
	/** short option character, or 0 if no short option */
	public char shortOption() default 0;

	/** long options, usually just one */
	public String[] longOptions();

	/** help text. will be formatted by splitting at spaces */
	public String help();

	/**
	 * metavariable for help text, used for generating a text like
	 * {@code --foo=METAVAR} in the help text
	 */
	public String metavar() default "";

	/** if <code>true</code>, this option always has to be specified */
	public boolean required() default false;
}
