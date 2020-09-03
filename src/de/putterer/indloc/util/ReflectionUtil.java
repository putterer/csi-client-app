package de.putterer.indloc.util;

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


	public static void setPrivateFinalField(Field field, Object target, Object value) throws NoSuchFieldException, IllegalAccessException {
		field.setAccessible(true);

		Field modifiersField = Field.class.getDeclaredField("modifiers");
		modifiersField.setAccessible(true);
		modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

		field.set(target, value);
	}
}
