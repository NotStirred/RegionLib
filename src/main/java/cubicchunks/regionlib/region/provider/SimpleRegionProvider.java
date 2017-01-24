/*
 *  This file is part of RegionLib, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2016 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.regionlib.region.provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.IRegionKey;
import cubicchunks.regionlib.region.IRegion;
import cubicchunks.regionlib.region.Region;

/**
 * A simple implementation of IRegionProvider, this is intended to be used together with CachedRegionProvider or other
 * caching implementation
 *
 * @param <R> The region key type
 * @param <L> The key type
 */
public class SimpleRegionProvider<R extends IRegionKey<R, L>, L extends IKey<R, L>> implements IRegionProvider<R, L> {

	private Path directory;
	private RegionFactory<R, L> regionBuilder;
	private Function<String, R> nameToRegionKey;
	private Map<R, IRegion<R, L>> toReturn;

	public SimpleRegionProvider(Path directory, RegionFactory<R, L> regionBuilder, Function<String, R> nameToRegionKey) {
		this.directory = directory;
		this.regionBuilder = regionBuilder;
		this.nameToRegionKey = nameToRegionKey;
		this.toReturn = new HashMap<>();
	}

	@Override public IRegion<R, L> getRegion(R regionKey) throws IOException {
		Path regionPath = directory.resolve(regionKey.getRegionName());

		IRegion<R, L> reg = regionBuilder.create(regionPath, regionKey);

		this.toReturn.put(regionKey, reg);
		return reg;
	}

	@Override public Optional<IRegion<R, L>> getRegionIfExists(R regionKey) throws IOException {
		Path regionPath = directory.resolve(regionKey.getRegionName());
		if (!Files.exists(regionPath)) {
			return Optional.empty();
		}
		IRegion<R, L> reg = regionBuilder.create(regionPath, regionKey);
		this.toReturn.put(regionKey, reg);
		return Optional.of(reg);
	}

	@Override public void returnRegion(R key) throws IOException {
		IRegion<?, ?> reg = toReturn.remove(key);
		if (reg == null) {
			throw new IllegalArgumentException("No region found");
		}
		reg.close();
	}

	@Override public Iterator<R> allRegions() throws IOException {
		return Files.list(directory)
			.map(Path::getFileName)
			.map(Path::toString)
			.map(nameToRegionKey::apply)
			.iterator();
	}

	@Override public void close() throws IOException {
		if (!toReturn.isEmpty()) {
			System.err.println("Warning: leaked " + toReturn.size() + " regions! Closing them now");
			for (IRegion<?, ?> r : toReturn.values()) {
				r.close();
			}
			toReturn.clear();
			toReturn = null;
		}
	}

	public static <R extends IRegionKey<R, L>, L extends IKey<R, L>> SimpleRegionProvider<R, L> createDefault(Path directory, Function<String, R> nameToRegionKey, int sectorSize) {
		return new SimpleRegionProvider<>(directory, (p, r) ->
			new Region.Builder<R, L>()
				.setPath(p)
				.setEntriesPerRegion(r.getKeyCount())
				.setSectorSize(sectorSize)
				.build(),
			nameToRegionKey
		);
	}

	@FunctionalInterface
	public interface RegionFactory<R extends IRegionKey<R, L>, L extends IKey<R, L>> {
		IRegion<R, L> create(Path path, R key) throws IOException;
	}
}