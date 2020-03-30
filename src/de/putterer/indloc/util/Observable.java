package de.putterer.indloc.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An util class representing an observable value
 * @author Fabian Putterer
 *
 * @param <T> The type of the encapsulated value
 */
public class Observable<T> {
	private static final ExecutorService executorService = Executors.newFixedThreadPool(4);

	private T value;
	private Map<InvalidationListener<T>, Boolean> invalidationListener = new HashMap<>();
	private Map<ChangeListener<T>, Boolean> changeListener = new HashMap<>();
	
	public Observable(T value) {
		this.value = value;
	}
	
	public T get() {
		return value;
	}
	
	public void set(T newValue) {
		T oldValue = value;
		this.value = newValue;
		changeListener.forEach((listener, async) -> {
			if(async) {
				executorService.submit(() -> listener.onChange(oldValue, newValue));
			} else {
				listener.onChange(oldValue, newValue);
			}
		});
		invalidate();
	}
	
	public void invalidate() {
		invalidationListener.forEach((listener, async) -> {
			if (async) {
				executorService.submit(listener::invalidated);
			} else {
				listener.invalidated();
			}
		});
	}
	
	/**
	 * Binds this observable to another observable, not async, may create cyclic dependency!
	 * @param observable The observable to bind to
	 */
	public void bind(Observable<T> observable) {
		observable.addListener((_void, newValue) -> this.set(newValue), false);
	}
	
	public void addListener(InvalidationListener<T> listener, boolean async) {
		invalidationListener.put(listener, async);
	}
	
	public void addListener(ChangeListener<T> listener, boolean async) {
		changeListener.put(listener, async);
	}

	public void removeListener(InvalidationListener<T> listener) {
		invalidationListener.remove(listener);
	}

	public void removeListener(ChangeListener<T> listener) {
		changeListener.remove(listener);
	}
	
	@FunctionalInterface
	public static interface InvalidationListener<T> {
		public void invalidated();
	}
	
	@FunctionalInterface
	public static interface ChangeListener<T> {
		public void onChange(T oldValue, T newValue);
	}
}
