package de.putterer.indloc.util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReflectionUtil {

	/**
	 * Get all fields of a class (including ones declared by superclasses) using reflection
	 * @param clazz the class to analyze
	 * @return the discovered fields
	 */
	public static List<Field> getFields(Class clazz) {
		List<Field> res = new ArrayList<>();
		res.addAll(Arrays.asList(clazz.getDeclaredFields()));
		if(clazz != Object.class) {
			res.addAll(getFields(clazz.getSuperclass()));
		}
		return res;
	}

	private static VarHandle modifiersField;
	static {
		try {
			modifiersField = MethodHandles.privateLookupIn(Field.class, MethodHandles.lookup()).findVarHandle(Field.class, "modifiers", int.class);
		} catch (IllegalAccessException | NoSuchFieldException e) {
			Logger.error("Couldn't get handle of modifiers field", e);
			e.printStackTrace();
			modifiersField = null;
		}
	}

	public static void setPrivateFinalField(Field field, Object target, Object value) throws NoSuchFieldException, IllegalAccessException {
		field.setAccessible(true);

		int modifiers = field.getModifiers();
		if(Modifier.isFinal(modifiers)) {
			modifiersField.set(field, modifiers & ~Modifier.FINAL);
		}

		field.set(target, value);
	}
}
