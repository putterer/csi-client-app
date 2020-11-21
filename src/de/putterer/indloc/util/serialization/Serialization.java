package de.putterer.indloc.util.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.putterer.indloc.Station;
import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.data.DataInfo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility code for (de-)serializing objects
 */
public class Serialization {

	private static final Gson gson;

	static {
		// StationSerializationAdapter internally USES ITS OWN GSON
		gson = new GsonBuilder()
				.registerTypeAdapter(CSIInfo.class, new CSIInfoInterfaceAdapter())
				.registerTypeAdapter(Station.class, new StationSerializationAdapter())
				.create();
	}

	public static void save(Path path, DataInfo... info) throws FileNotFoundException, IOException {
		serialize(path, info);
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
	
}
