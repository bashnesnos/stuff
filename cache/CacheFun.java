import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static java.lang.System.out;

public class CacheFun {

	public static final String KEY_FORMAT = "key_%d";
	
	public static final String FIFO_TYPE_NAME = "FIFO";
	public static final String LRU_TYPE_NAME = "LRU";
	
	private static NaiveFIFOCache<String, Object> naiveFIFO;
	private static NaiveLRUCache<String, Object> naiveLRU;
	
	private static LinkedFIFOCache<String, Object> linkedFIFO;
	private static LinkedLRUCache<String, Object> linkedLRU;
	
	public static void main(String[] args) {
		int size = 33;
		
		if (args != null && args.length == 1) {
			try {
				size = Integer.valueOf(args[0]);
			}
			catch (NumberFormatException ex) {
				out.println("Wrong number: " + args[0]);
			}
		}
		
		
		linkedFIFO = new LinkedFIFOCache<String, Object>(size);

		//keySet iteration matters
		for (int i = 0; i < size; i++) {
			linkedFIFO.put(String.format(KEY_FORMAT, i), new Object());
		}
		
		naiveFIFO = new NaiveFIFOCache<String, Object>(linkedFIFO);
		naiveLRU =  new NaiveLRUCache<String, Object>(linkedFIFO);
		linkedLRU = new LinkedLRUCache<String, Object>(linkedFIFO);

		
		checkNoGet();
		checkWithGet();
		checkRotation();
		checkRotationGets();
		
		out.println("\nSmoke test successfull");
	}

	static void checkNoGet() {
		String extraKey = "noGetKey";
		out.println("\nNo Get");
		
		naiveFIFO.put(extraKey, new Object());
		naiveLRU.put(extraKey, new Object());
		linkedFIFO.put(extraKey, new Object());
		linkedLRU.put(extraKey, new Object());

		checkConsistency();
		
	}

	static void checkWithGet() {
		String extraKey = "withGetKey";
		out.println("\nWith Get");
		
		for (int i = 0; i < naiveFIFO.size(); i++) {
			String key = String.format(KEY_FORMAT, i);
			naiveFIFO.get(key);
			naiveLRU.get(key);
			linkedFIFO.get(key);
			linkedLRU.get(key);
		}
		
		naiveFIFO.put(extraKey, new Object());
		naiveLRU.put(extraKey, new Object());
		linkedFIFO.put(extraKey, new Object());
		linkedLRU.put(extraKey, new Object());

		checkConsistency();
				
	}	

	static void checkRotation() {
		out.println("\nRotation");
		
		for (int i = 0; i < naiveFIFO.size()*3; i++) {
			String key = String.format("new_key_%d", i);
			Object val = new Object();
			naiveFIFO.put(key, val);
			naiveLRU.put(key, val);
			linkedFIFO.put(key, val);
			linkedLRU.put(key, val);
		}

		checkConsistency();
				
	}
	
	
	static void checkRotationGets() {
		out.println("\nRotation and gets");
		
		String pickedKey = linkedLRU.keySet().iterator().next();
		
		for (int i = 0; i < naiveFIFO.size()*17; i++) {
			String key = String.format("new_key_%d", i);
			Object val = new Object();
			naiveFIFO.get(pickedKey);
			naiveFIFO.put(key, val);
			naiveLRU.get(pickedKey);
			naiveLRU.put(key, val);
			linkedFIFO.get(pickedKey);
			linkedFIFO.put(key, val);
			linkedLRU.get(pickedKey);
			linkedLRU.put(key, val);
		}

		checkConsistency();
		
		assertTrue("Linked FIFO should NOT contain frequently used key", linkedFIFO.get(pickedKey) == null);
		assertTrue("Naive FIFO should NOT contain frequently used key", naiveFIFO.get(pickedKey) == null);


		assertTrue("Linked LRU should contain frequently used key", linkedLRU.get(pickedKey) != null);
		assertTrue("Naive LRU should contain frequently used key", naiveLRU.get(pickedKey) != null);
				
	}
	
	static void checkConsistency() {
		printMapsKeySet(FIFO_TYPE_NAME, linkedFIFO, naiveFIFO);

		assertSizeAndKeysMatch(linkedFIFO, naiveFIFO);
		
		printMapsKeySet(LRU_TYPE_NAME, linkedLRU, naiveLRU);
		
		assertSizeAndKeysMatch(linkedLRU, naiveLRU);
	}
	
	static void printMapsKeySet(String type, Map<?, ?> linked , Map<?, ?> naive) {
		out.println("\n" + type);
		out.println("Naive");
		out.println(naive.keySet());
		out.println("Linked");
		out.println(linked.keySet());
	}
	
	static void assertSizeAndKeysMatch(Map<?, ?> linked, Map<?, ?> naive) {
		assertTrue("Naive too big", naive.size() <= linked.size());
		assertTrue("Key sets don't match", linked.keySet().containsAll(naive.keySet()));
	}
	
	static void assertTrue(String msg, boolean condition) {
		if (!condition) {
			throw new AssertionError(msg);
		}
	}
	
}

abstract class NaiveAbstractLinkedFixedSizeHashMap<K, V> extends HashMap<K, V> {
	private static final long serialVersionUID = 5781534453381741187L;
	
	private final int maxCapacity;
	protected final LinkedList<K> keyQueue = new LinkedList<K>();
	
	public NaiveAbstractLinkedFixedSizeHashMap(int size) {
		super(size, 1.0f);
		maxCapacity = size;
	}
	
	public NaiveAbstractLinkedFixedSizeHashMap(Map<? extends K, ? extends V> sourceMap) {
		super(sourceMap.size(), 1.0f);
		for (Map.Entry<? extends K, ? extends V> newEntry : sourceMap.entrySet()) {
			K nextKey = newEntry.getKey();
			markNew(nextKey);
			super.put(nextKey, newEntry.getValue());
		}		
		maxCapacity = sourceMap.size();
	}
	
	@Override
	public V put(K key, V val) {
		if (size() == maxCapacity) {
			removeOld();
		}
		markNew(key);
		return super.put(key, val);
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> sourceMap) {
		if (sourceMap != null) {
			int currentSize = size();
			int sourceMapSize = sourceMap.size();
			if (sourceMapSize > maxCapacity || currentSize + sourceMapSize > maxCapacity) {
				Set<? extends K> newEntries = new HashSet<K>(sourceMap.keySet());
				newEntries.removeAll(keySet()); //would add only new unique
				Iterator<? extends K> newEntriesIterator = newEntries.iterator();
				while(newEntriesIterator.hasNext() && size() < maxCapacity) {
					K nextKey = newEntriesIterator.next();
					markNew(nextKey);
					super.put(nextKey, sourceMap.get(nextKey)); //bypassing cleanUp
				}
			}
			else {
				for (Map.Entry<? extends K, ? extends V> newEntry : sourceMap.entrySet()) {
					K nextKey = newEntry.getKey();
					markNew(nextKey);
					super.put(nextKey, newEntry.getValue());
				}
			}
		}
	}
	
	protected void removeOld() {
		K head = keyQueue.pop();
		remove(head);
	}

	protected void markNew(K key) {
		keyQueue.add(key);
	}
}

class NaiveFIFOCache<K, V> extends NaiveAbstractLinkedFixedSizeHashMap<K, V> {

	private static final long serialVersionUID = 3366536082570082694L;

	public NaiveFIFOCache(int size) {
		super(size);
	}

	public NaiveFIFOCache(Map<? extends K, ? extends V> sourceMap) {
		super(sourceMap);
	}

}

class NaiveLRUCache<K, V> extends NaiveAbstractLinkedFixedSizeHashMap<K, V> {

	private static final long serialVersionUID = 3366536082570082694L;

	public NaiveLRUCache(int size) {
		super(size);
	}

	public NaiveLRUCache(Map<? extends K, ? extends V> sourceMap) {
		super(sourceMap);
	}

	@SuppressWarnings("unchecked")
	@Override
	public V get(Object key) {
		int idxToMakeLast = keyQueue.indexOf(key);
		if (idxToMakeLast != -1) {
			keyQueue.remove(idxToMakeLast);			
			keyQueue.add((K) key); //passed indexOf equals check; bad K.equals might cause ClassCastException, so it's dirty
		}
		return super.get(key);
	}
	
}

class LinkedFIFOCache<K, V> extends LinkedHashMap<K, V> {
	private static final long serialVersionUID = 3106623977485295065L;
	private final int maxCapacity;
	
	public LinkedFIFOCache(int size) {
		super(size, 1.0f);
		maxCapacity = size;
	}
	
	public LinkedFIFOCache(Map<? extends K, ? extends V> sourceMap) {
		super(sourceMap);
		maxCapacity = sourceMap.size();
	}		

	@Override
	protected boolean removeEldestEntry(Entry<K, V> arg0) {
		return size() > maxCapacity;
	}
	
}

class LinkedLRUCache<K, V> extends LinkedHashMap<K, V> {
	private static final long serialVersionUID = 8670440239485057291L;
	private final int maxCapacity;
	
	public LinkedLRUCache(int size) {
		super(size, 1.0f, true);
		maxCapacity = size;
	}
	
	public LinkedLRUCache(Map<? extends K, ? extends V> sourceMap) {
		super(sourceMap.size(), 1.0f, true);
		maxCapacity = sourceMap.size();
		putAll(sourceMap);
	}		

	@Override
	protected boolean removeEldestEntry(Entry<K, V> arg0) {
		return size() > maxCapacity;
	}
	
}