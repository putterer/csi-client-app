package de.putterer.indloc.util;

import java.util.ArrayList;
import java.util.List;

/**
 * An util class representing an observable value
 * @author Fabian Putterer
 *
 * @param <T> The type of the encapsulated value
 */
public class Observable<T> {
	private T value;
	private List<InvalidationListener<T>> invalidationListener = new ArrayList<>();
	private List<ChangeListener<T>> changeListener = new ArrayList<>();
	
	public Observable(T value) {
		this.value = value;
	}
	
	public T get() {
		return value;
	}
	
	public void set(T newValue) {
		T oldValue = value;
		this.value = newValue;
		changeListener.forEach(l -> l.onChange(oldValue, newValue));
		invalidate();
	}
	
	public void invalidate() {
		invalidationListener.forEach(InvalidationListener::invalidated);
	}
	
	/**
	 * Binds this observable to another observable, not async, may create cyclic dependency!
	 * @param observable The observable to bind to
	 */
	public void bind(Observable<T> observable) {
		observable.addListener((_void, newValue) -> this.set(newValue));
	}
	
	public void addListener(InvalidationListener<T> listener) {
		invalidationListener.add(listener);
	}
	
	public void addListener(ChangeListener<T> listener) {
		changeListener.add(listener);
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
