package de.putterer.indloc.util.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.putterer.indloc.Station;
import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.util.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

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

	public static void save(Path path, boolean compress, DataInfo... info) throws IOException {
		serialize(path, compress, info);
	}

	public static void save(Path path, DataInfo... info) throws IOException {
		save(path, true, info);
	}

	public static void saveLegacy(Path path, CSIInfo... csi) throws IOException {
		serializeLegacy(path, csi);
	}
	
	public static CSIInfo[] read(Path path) throws FileNotFoundException, IOException, ClassNotFoundException {
		return deserialize(path, CSIInfo[].class);
	}

	public static CSIInfo[] readLegacy(Path path) throws FileNotFoundException, IOException, ClassNotFoundException {
		return deserializeLegacy(path);
	}

	public static void serialize(Path path, boolean compress, Object obj) throws IOException {
		String json = gson.toJson(obj);
		byte[] serializedData = json.getBytes(StandardCharsets.UTF_8);

		if(compress) {
			byte[] compressedData = new byte[serializedData.length];
			Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
			deflater.setInput(serializedData);
			deflater.finish();
			int n = deflater.deflate(compressedData);
			deflater.end();
			compressedData = Arrays.copyOfRange(compressedData, 0, n);

			Files.write(path, compressedData);
		} else {
			Files.write(path, serializedData);
		}
	}

	public static <T> T deserialize(Path path, Class<T> clazz) throws IOException {
		byte[] data = Files.readAllBytes(path);

		if(path.getFileName().toString().endsWith(".deflate")) {
			byte[] uncompressedData = new byte[64000];
			Inflater inflater = new Inflater();
			inflater.setInput(data);
			int n = 0;
			try {
				n = inflater.inflate(uncompressedData);
			} catch (DataFormatException e) {
				Logger.error("Couldn't detect format of compressed data", e);
			}
			inflater.end();

			data = Arrays.copyOfRange(uncompressedData, 0, n);
		}

		String json = new String(data, StandardCharsets.UTF_8);
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
