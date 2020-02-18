package de.putterer.indloc.util;

import lombok.Data;
import lombok.val;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Utility code for interpolating between given steps
 */
public class Interpolation {
	private final TreeMap<Integer, Step> steps = new TreeMap<>();
	
	public Interpolation(Step... steps) {
		for(Step step : steps) {
			this.steps.put(step.getLevel(), step);//TODO: if levels is sorted this is the worst way to build a red-black-tree
		}
	}
	
	public int interpolate(float level) {
		val ceil = steps.ceilingEntry((int)Math.ceil(level));
		val floor = steps.floorEntry((int)Math.floor(level));
		
		if(ceil == null) {
			return floor.getValue().getValue();
		} else if(floor == null) {
			return ceil.getValue().getValue();
		} else if(floor.getKey() == ceil.getKey()) {
			return floor.getValue().getValue();
		}
		
		float pct = (level - (float)floor.getKey()) / (float)(ceil.getKey() - floor.getKey());
		return (int) (floor.getValue().getValue() + (float)(ceil.getValue().getValue() - floor.getValue().getValue()) * pct); 
	}
	
	public Map<Integer, Step> getSteps() {
		return Collections.unmodifiableMap(steps);
	}
	
	@Data
	public static class Step {
		private final int level; // the level / key of this step
		private final int value; // the value of the resulting interpolation at this step
	}
}
