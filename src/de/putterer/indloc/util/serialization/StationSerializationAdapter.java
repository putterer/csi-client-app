package de.putterer.indloc.util.serialization;

import com.google.gson.*;
import de.putterer.indloc.Station;
import de.putterer.indloc.csi.intel.IntCSIInfo;
import de.putterer.indloc.util.ReflectionUtil;

import java.lang.reflect.Type;

public class StationSerializationAdapter implements JsonDeserializer<Station>, JsonSerializer<Station> {

	private static final Gson plainGson = new Gson();
	private static final String DATA_TYPE_FIELD_NAME = "dataTypeClassName";

	@Override
	public Station deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
		Station station = plainGson.fromJson(jsonElement, Station.class);

		JsonElement clazzName = jsonElement.getAsJsonObject().get(DATA_TYPE_FIELD_NAME);
		Class<?> clazz = clazzName == null ? IntCSIInfo.class : classForName(clazzName.getAsString());

		try {
			ReflectionUtil.setPrivateFinalField(Station.class.getDeclaredField("dataType"), station, clazz);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new JsonParseException(e);
		}

		return station;
	}

	@Override
	public JsonElement serialize(Station station, Type type, JsonSerializationContext jsonSerializationContext) {
		JsonElement obj = jsonSerializationContext.serialize(station);
		obj.getAsJsonObject().addProperty(DATA_TYPE_FIELD_NAME, station.getDataType().getName());
		return obj;
	}

	private static Class<?> classForName(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			throw new JsonParseException(e);
		}
	}
}
