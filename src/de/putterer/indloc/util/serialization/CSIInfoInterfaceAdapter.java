package de.putterer.indloc.util.serialization;

import com.google.gson.*;
import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.csi.atheros.AthCSIInfo;
import de.putterer.indloc.csi.esp.EspCSIInfo;
import de.putterer.indloc.csi.intel.IntCSIInfo;

import java.lang.reflect.Type;
import java.util.Optional;

public class CSIInfoInterfaceAdapter implements JsonDeserializer<CSIInfo>, JsonSerializer<CSIInfo> {

	@Override
	public CSIInfo deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
		Optional<Class<?>> clazz = Optional.ofNullable(jsonElement.getAsJsonObject().get("csiInfoType"))
				.map(JsonElement::getAsString)
				.map(CSIInfoInterfaceAdapter::classForName);


		if(clazz.isPresent()) {
			jsonElement.getAsJsonObject().remove("csiInfoType");
		} else {
			clazz = Optional.of(jsonElement.getAsJsonObject().get("intelCsiNotification") != null ? IntCSIInfo.class : (jsonElement.getAsJsonObject().get("firstWordInvalid") != null ? EspCSIInfo.class : AthCSIInfo.class));
		}

		return jsonDeserializationContext.deserialize(jsonElement, clazz.get());
	}

	@Override
	public JsonElement serialize(CSIInfo info, Type type, JsonSerializationContext jsonSerializationContext) {
		JsonElement obj = jsonSerializationContext.serialize(info);
		if(obj instanceof JsonObject) {
			obj.getAsJsonObject().addProperty("csiInfoType", info.getClass().toString());
		}
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
