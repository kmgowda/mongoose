package com.emc.mongoose.common.io;

import com.emc.mongoose.common.conf.Constants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Created on 04.04.16.
 */
public class JsonUtil {

	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	/**
	 *
	 * @param pathString - the string of the path to a file
	 * @return json that contains the file tree if the file is a directory,
	 * or the name of the file otherwise
	 * @throws JsonProcessingException - an exception of the json building
	 */
	public static String jsonPathContent(final String pathString) throws JsonProcessingException {
		return jsonPathContent(new File(pathString));
	}

	/**
	 *
	 * @param file - some file
	 * @return json that contains the file tree if the file is a directory,
	 * or the name of the file otherwise
	 * @throws JsonProcessingException - an exception of the json building
	 */
	public static String jsonPathContent(final File file) throws JsonProcessingException {
		final List<Object> dirContent = new ArrayList<>();
		if (file.isFile()) {
			return file.getName();
		} else {
			listDirectoryContents(file.toPath(), dirContent);
			final Map<String, List<Object>> fileTree = new HashMap<>();
			fileTree.put(file.getName(), dirContent);
			return JSON_MAPPER.writeValueAsString(fileTree);
		}
	}

	public static String jsArrayPathContent(final String pathString) throws
			JsonProcessingException {
		return jsArrayPathContent(new File(pathString));
	}

	public static String jsArrayPathContent(final File file) throws JsonProcessingException {
		final List<Object> dirContent = new ArrayList<>();
		if (file.isFile()) {
			return file.getName();
		} else {
			listDirectoryContents(file.toPath(), dirContent);
			return JSON_MAPPER.writeValueAsString(dirContent);
		}
	}

	public static String jsArrayPathContent(final Path path) throws JsonProcessingException {
		return jsArrayPathContent(path.toFile());
	}

	private static void listDirectoryContents(final Path dirPath, final List<Object> rootList) {
		try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
			Map<String, Object> dirMap;
			List<Object> subList;
			for (final Path file : stream) {
				if (file.toFile().isDirectory()) {
					dirMap = new HashMap<>();
					subList = new ArrayList<>();
					dirMap.put(file.getFileName().toString(), subList);
					rootList.add(dirMap);
					listDirectoryContents(file.toAbsolutePath(), subList);
				} else {
					rootList.add(file.getFileName().toString());
				}
			}
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

}
