package water.compiler.util;

/**
 *
 * Utility class for a pair of values
 *
 * @param <K> The first value type
 * @param <V> The second value type
 */
public class Pair<K, V> {
	private final K first;
	private final V second;

	public Pair(K first, V second) {
		this.first = first;
		this.second = second;
	}

	public K getFirst() {
		return first;
	}

	public V getSecond() {
		return second;
	}
}
