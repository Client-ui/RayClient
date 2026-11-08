package dev.ray;

import net.minecraft.client.option.SimpleOption;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Sets the brightness (gamma) option past its normal 100% limit, which is what Fullbright needs.
 *
 * SimpleOption#setValue throws away anything outside 0..1, so the value has to be written to the field directly.
 * The field is found by its shape instead of its name (the only non-static, non-final field of type Object in
 * SimpleOption is the stored value), so this works both in a dev environment and in the normal release game,
 * where names are obfuscated. No mixin involved. If it cannot be found, set() returns false and Fullbright falls
 * back to a night vision effect.
 */
public final class Gamma {
	private static Field valueField;
	private static boolean searched = false;

	private Gamma() {}

	public static boolean set(SimpleOption<Double> option, double value) {
		Field f = find(option);
		if (f == null) return false;
		try {
			f.set(option, Double.valueOf(value));
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	private static Field find(SimpleOption<Double> option) {
		if (searched) return valueField;
		searched = true;
		try {
			Object current = option.getValue();
			for (Field f : SimpleOption.class.getDeclaredFields()) {
				int mod = f.getModifiers();
				if (Modifier.isStatic(mod) || Modifier.isFinal(mod) || f.getType() != Object.class) continue;
				f.setAccessible(true);
				if (f.get(option) == current) {
					valueField = f;
					break;
				}
			}
		} catch (Throwable ignored) {
			valueField = null;
		}
		return valueField;
	}
}
