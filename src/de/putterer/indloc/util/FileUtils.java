package de.putterer.indloc.util;

import lombok.SneakyThrows;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class FileUtils {
	
	// Copied from my own code at https://github.com/FAForever/Neroxis-Map-Generator/blob/develop/src/java/util/FileUtils.java
	/**
	 * Deletes a file and its contents (if directory) recursively
	 * @param path
	 */
	@SneakyThrows
	public static void deleteRecursiveIfExists(Path path) {
		if(!Files.exists(path)) {
			return;
		}

		if(Files.isDirectory(path)) {
			Stream<Path> files = Files.list(path);
			files.forEach(FileUtils::deleteRecursiveIfExists);
			files.close();
		}

		Files.delete(path);
	}

	/**
	 * loads an image
	 * @param path the path to the image file
	 * @return the image
	 */
	@SneakyThrows
	public static BufferedImage loadImage(Path path) {
		return ImageIO.read(path.toFile());
	}

	/**
	 * loads an image
	 * @param path the path to the image file
	 * @return the image
	 */
	public static BufferedImage loadImage(String path) {
		return loadImage(Paths.get(path));
	}
}
