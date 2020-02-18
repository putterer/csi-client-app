package de.putterer.indloc.util;

import com.google.gson.Gson;
import de.putterer.indloc.csi.CSIInfo;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility code for (de-)serializing objects
 */
public class Serialization {

	private static final Gson gson = new Gson();

	public static void save(Path path, CSIInfo... csi) throws FileNotFoundException, IOException {
		serialize(path, csi);
	}

	public static void saveLegacy(Path path, CSIInfo... csi) throws FileNotFoundException, IOException {
		serializeLegacy(path, csi);
	}
	
	public static CSIInfo[] read(Path path) throws FileNotFoundException, IOException, ClassNotFoundException {
		return deserialize(path, CSIInfo[].class);
	}

	public static CSIInfo[] readLegacy(Path path) throws FileNotFoundException, IOException, ClassNotFoundException {
		return deserializeLegacy(path);
	}

	public static void serialize(Path path, Object obj) throws IOException {
		String json = gson.toJson(obj);
		Files.write(path, json.getBytes(StandardCharsets.UTF_8));
	}

	public static <T> T deserialize(Path path, Class<T> clazz) throws IOException {
		String json = new String(Files.readAllBytes(path));
		return gson.fromJson(json, clazz);
	}
	
	public static void serializeLegacy(Path path, Object obj) throws FileNotFoundException, IOException {
		ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path.toFile()));
		out.writeObject(obj);
		out.flush();
		out.close();
	}
	
	public static <T> T deserializeLegacy(Path path) throws FileNotFoundException, IOException, ClassNotFoundException {
		ObjectInputStream in = new ObjectInputStream(new FileInputStream(path.toFile()));
		Object o = in.readObject();
		in.close();
		return (T)o;
	}

	/**
	 * Get all fields of a class (including ones declared by superclasses) using reflection
	 * @param clazz the class to analyze
	 * @return the discovered fields
	 */
	private static List<Field> getFields(Class clazz) {
		List<Field> res = new ArrayList<>();
		res.addAll(Arrays.asList(clazz.getDeclaredFields()));
		if(clazz != Object.class) {
			res.addAll(getFields(clazz.getSuperclass()));
		}
		return res;
	}
	
}
