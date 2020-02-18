package de.putterer.indloc.util;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;


/**
 * A simple vector util class
 * taken from: https://github.com/Geosearchef/rtsIO/blob/master/src/de/geosearchef/rtsIO/util/Vector.java
 */
public class Vector implements Serializable {
	@Getter @Setter private float x;
	@Getter @Setter private float y;

	public Vector(float x, float y) {
		this.x = x;
		this.y = y;
	}

	public Vector() {
		this.x = 0f;
		this.y = 0f;
	}

	public Vector(Vector src) {
		this.x = src.x;
		this.y = src.y;
	}

	public Vector add(Vector v) {
		return new Vector(this.x + v.x, this.y + v.y);
	}

	public Vector sub(Vector v) {
		return new Vector(this.x - v.x, this.y - v.y);
	}

	public Vector scale(float s) {
		return new Vector(this.x * s, this.y * s);
	}

	public Vector negate() {
		return new Vector(-this.x, -this.y);
	}

	public float lengthSquared() {
		return this.x * this.x + this.y * this.y;
	}

	public float length() {
		return (float) Math.sqrt(this.lengthSquared());
	}

	public Vector normalise() {
		return this.scale(1f / this.length());
	}

	public Vector normaliseOrElse(Vector v) {
		float length = this.length();
		return length > 0 ? this.scale(1f / length) : v;
	}

	public Vector normaliseOr1() {
		return normaliseOrElse(new Vector(1f, 0f));
	}

	public float dot(Vector v) {
		return v.x * this.x + v.y * this.y;
	}

	@Override
	public String toString() {
		return "Vector: [" + x + "|" + y +  "]";
	}
}
