package de.putterer.indloc.util.serialization;

import com.google.gson.*;
import de.putterer.indloc.csi.atheros.AthCSIInfo;
import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.csi.esp.EspCSIInfo;
import de.putterer.indloc.csi.intel.IntCSIInfo;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.data.ecg.EcgInfo;

import java.lang.reflect.Type;
import java.util.Optional;

public class DataInfoInterfaceAdapter implements JsonDeserializer<DataInfo>, JsonSerializer<DataInfo> {

	@Override
	public DataInfo deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
		Optional<Class<?>> clazz = Optional.ofNullable(jsonElement.getAsJsonObject().get("csiInfoType"))
				.map(JsonElement::getAsString)
				.map(DataInfoInterfaceAdapter::classForName);


		if(clazz.isPresent()) {
			jsonElement.getAsJsonObject().remove("csiInfoType");
		} else {
			clazz = Optional.of(getClassBasedOnProperties(jsonElement));
		}

		return jsonDeserializationContext.deserialize(jsonElement, clazz.get());
	}

	private Class<?> getClassBasedOnProperties(JsonElement jsonElement) {
		if(jsonElement.getAsJsonObject().get("atherosCsiStatus") != null) {
			return AthCSIInfo.class;
		} else if(jsonElement.getAsJsonObject().get("intelCsiNotification") != null) {
			return IntCSIInfo.class;
		} else if(jsonElement.getAsJsonObject().get("firstWordInvalid") != null) {
			return EspCSIInfo.class;
		} else if(jsonElement.getAsJsonObject().get("calibration") != null) {
			return AndroidInfo.class;
		} else if(jsonElement.getAsJsonObject().get("value") != null) {
			return EcgInfo.class;
		} else {
			return DataInfo.class;
		}
	}

	@Override
	public JsonElement serialize(DataInfo info, Type type, JsonSerializationContext jsonSerializationContext) {
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
