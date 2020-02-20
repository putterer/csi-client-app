package de.putterer.indloc.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Consumer;

@Getter
@RequiredArgsConstructor
public class DataConsumer<T> {
	private final Class<T> type;
	private final Consumer<T> consumer;
}
