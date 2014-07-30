import static java.lang.System.out;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CacheFun {

	public static final String KEY_FORMAT = "key_%d";
	
	public static final String FIFO_TYPE_NAME = "FIFO";
	public static final String LRU_TYPE_NAME = "LRU";
	
	private static NaiveFIFOCache<String, Object> naiveFIFO;
	private static NaiveLRUCache<String, Object> naiveLRU;
	
	private static LinkedFIFOCache<String, Object> linkedFIFO;
	private static LinkedLRUCache<String, Object> linkedLRU;

        private static boolean printKeys = false;
        
	public static void main(String[] args) {
		int size = 300000;
		
		if (args != null && args.length == 1) {
			try {
				size = Integer.valueOf(args[0]);
			}
			catch (NumberFormatException ex) {
				out.println("Wrong number: " + args[0]);
			}
		}
		
		
                linkedFIFO = new LinkedFIFOCache<String, Object>(1000);

		//keySet iteration order matters
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
                
                linkedFIFO = new LinkedFIFOCache<String, Object>(size);

		//keySet iteration order matters
		for (int i = 0; i < size; i++) {
			linkedFIFO.put(String.format(KEY_FORMAT, i), new Object());
		}
		
		naiveFIFO = new NaiveFIFOCache<String, Object>(linkedFIFO);
                if (size > 30000) { //otherwise too slow
                    naiveLRU = new NaiveLRUCache<String, Object>(30000);
                    naiveLRU.putAll(linkedFIFO);
                }
                else {
                    naiveLRU =  new NaiveLRUCache<String, Object>(linkedFIFO);
                }
		linkedLRU = new LinkedLRUCache<String, Object>(linkedFIFO);
                
                ExecutorService loadThreadPool = Executors.newFixedThreadPool(4);
                
                int iterationsNum = 100000;
               
                if (args != null && args.length == 2) {
			try {
                            iterationsNum = Integer.valueOf(args[1]);
			}
			catch (NumberFormatException ex) {
                            out.println("Wrong number: " + args[1]);
			}
		}
                
                out.println("\nMap size: " + size + "; number of iterations: " + iterationsNum);
                
                Future<Long> naiveFIFOFuture = loadThreadPool.submit(new MoreGetsCacheLoadCheckCallable(naiveFIFO, iterationsNum));                
                Future<Long> naiveLRUFuture = loadThreadPool.submit(new MoreGetsCacheLoadCheckCallable(naiveLRU, Math.min(100,iterationsNum)));
                Future<Long> linkedFIFOFuture = loadThreadPool.submit(new MoreGetsCacheLoadCheckCallable(linkedFIFO, iterationsNum));
                Future<Long> linkedLRUFuture = loadThreadPool.submit(new MoreGetsCacheLoadCheckCallable(linkedLRU, iterationsNum));
                
                try {

                    out.println("1. Linked FIFO millis: " + linkedFIFOFuture.get());
                    out.println("2. Naive FIFO millis: " + naiveFIFOFuture.get()); 
                    out.println("3. Linked LRU millis: " + linkedLRUFuture.get());
                    out.println("Naive LRU " + (size <= 30000 ? "" : "(size reduced to 30K) ") + (iterationsNum <= 100 ? "" : "(iter num reduced to 100)") + " millis: " + naiveLRUFuture.get()); //I bet it's the slowest
                    
                    loadThreadPool.shutdown();
                    loadThreadPool.awaitTermination(60, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                finally {
                    loadThreadPool.shutdownNow();
                }
   
	}

	static void checkNoGet() {
		String extraKey = "noGetKey";
		out.print("Testing put without gets: ");
		
		naiveFIFO.put(extraKey, new Object());
		naiveLRU.put(extraKey, new Object());
		linkedFIFO.put(extraKey, new Object());
		linkedLRU.put(extraKey, new Object());

		checkConsistency();
		
                out.println(" V");
	}

	static void checkWithGet() {
		String extraKey = "withGetKey";
		out.print("Testing put after gets: ");
		
		for (int i = 0; i < Math.min(1000, naiveFIFO.size()); i++) {
			String key = String.format("new_key_%d", i);
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
		
                out.println(" V");
	}	

	static void checkRotation() {
		out.print("Testing rotation: ");
		
		for (int i = 0; i < Math.min(1000, naiveFIFO.size())*2; i++) {
			String key = String.format("new_key_%d", i);
			Object val = new Object();
			naiveFIFO.put(key, val);
			naiveLRU.put(key, val);
			linkedFIFO.put(key, val);
			linkedLRU.put(key, val);
		}

		checkConsistency();
		
                out.println(" V");
                
	}
	
	
	static void checkRotationGets() {
		out.print("Testing rotation with intermittent gets: ");
		
		String pickedKey = linkedLRU.keySet().iterator().next();
		
		for (int i = 0; i < Math.min(1000, naiveFIFO.size())*2; i++) {
			String key = String.format(KEY_FORMAT, i);
			Object val = new Object();
			naiveFIFO.get(pickedKey);
			naiveFIFO.put(key, val);
                        naiveLRU.get(pickedKey);
                        naiveLRU.put(key, val);
                        linkedLRU.get(pickedKey);
                        linkedLRU.put(key, val);
			linkedFIFO.get(pickedKey);
			linkedFIFO.put(key, val);
		}

		checkConsistency();
		
		assertTrue("Linked FIFO should NOT contain frequently used key", linkedFIFO.get(pickedKey) == null);
		assertTrue("Naive FIFO should NOT contain frequently used key", naiveFIFO.get(pickedKey) == null);


		assertTrue("Linked LRU should contain frequently used key", linkedLRU.get(pickedKey) != null);
		assertTrue("Naive LRU should contain frequently used key", naiveLRU.get(pickedKey) != null);
				
                out.println(" V");
	}
	
	static void checkConsistency() {
		printMapsKeySet(FIFO_TYPE_NAME, linkedFIFO, naiveFIFO);

		assertSizeAndKeysMatch(linkedFIFO, naiveFIFO);
		
		printMapsKeySet(LRU_TYPE_NAME, linkedLRU, naiveLRU);
		
		assertSizeAndKeysMatch(linkedLRU, naiveLRU);
	}
	
	static void printMapsKeySet(String type, Map<?, ?> linked , Map<?, ?> naive) {
             if (printKeys) {
		out.println("\n" + type);
		out.println("Naive");
		out.println(naive.keySet());
		out.println("Linked");
		out.println(linked.keySet());
             }
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
		super(size + 3, 1.0f);
		maxCapacity = size;
	}
	
	public NaiveAbstractLinkedFixedSizeHashMap(Map<? extends K, ? extends V> sourceMap) {
		super(sourceMap.size() + 3, 1.0f);
		for (Map.Entry<? extends K, ? extends V> newEntry : sourceMap.entrySet()) {
			K nextKey = newEntry.getKey();
			markNew(nextKey);
			super.put(nextKey, newEntry.getValue());
		}		
		maxCapacity = sourceMap.size();
	}
	
	@Override
	public V put(K key, V val) {
                boolean existingKey = containsKey(key); //depends on hashCode & equals
                V result = super.put(key, val);
		if (size() > maxCapacity) {
			removeOld();
		}
                if (existingKey) {
                    updateKey(key);
                }
                else {
                    markNew(key);
                }
		return result;
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
        
        protected void updateKey(K key) {
		
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
            V result = super.get(key);
            if (result != null) {
                updateKey((K) key); //passed get equals check
            }
            return result;
	}

        @Override
        protected void updateKey(K key) { //source of troubles
            int idxToMakeLast = keyQueue.indexOf(key); //O(n)
            if (idxToMakeLast != -1) {
                    keyQueue.remove(idxToMakeLast); //O(n)			
                    keyQueue.add(key);
            }
	}
        
}

class LinkedFIFOCache<K, V> extends LinkedHashMap<K, V> {
	private static final long serialVersionUID = 3106623977485295065L;
	private final int maxCapacity;
	
	public LinkedFIFOCache(int size) {
		super(size + 3, 1.0f);
		maxCapacity = size;
	}
	
	public LinkedFIFOCache(Map<? extends K, ? extends V> sourceMap) {
		super(sourceMap.size() + 3, 1.0f, true);
		maxCapacity = sourceMap.size();
		putAll(sourceMap);
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
		super(size + 3 , 1.0f, true);
		maxCapacity = size;
	}
	
	public LinkedLRUCache(Map<? extends K, ? extends V> sourceMap) {
		super(sourceMap.size() + 3, 1.0f, true);
		maxCapacity = sourceMap.size();
		putAll(sourceMap);
	}		

	@Override
	protected boolean removeEldestEntry(Entry<K, V> arg0) {
		return size() > maxCapacity;
	}
	
}

class CacheLoadCheckCallable implements Callable<Long> {

    protected final Map<String, Object> testMap;
    protected final int opsPerIterCount;
    protected int iterations;
    protected int keyIdxOffset;

    public CacheLoadCheckCallable(Map<String, Object> testMap, int iterations) {
        this.iterations = iterations;
        this.testMap = testMap;
        this.keyIdxOffset = testMap.size();
        this.opsPerIterCount = keyIdxOffset < 10 ? 1 :  Math.round(keyIdxOffset * (keyIdxOffset < 10000 ? 0.1f : 0.001f));
    }

    @Override
    public Long call() throws Exception {

        long start = new Date().getTime();
        while(iterations > 0) {
            Iterator<String> keyIter = testMap.keySet().iterator();
            String[] opKeys = new String[opsPerIterCount];
            for (int i = 0; i < opsPerIterCount; i++) {
                if (keyIter.hasNext()) {
                    opKeys[i] = keyIter.next();
                }
                else if (i > 0) {
                    opKeys[i] = opKeys[i - 1];
                }
                else {
                    opKeys[i] = "dummy";
                }
            }

            for (int i = 0; i < opsPerIterCount; i++) {
                if (i % 2 == 0) {
                    testMap.get(opKeys[i]);
                }
                else if (i % 3 == 0) {
                    testMap.get(opKeys[i]);
                    testMap.put(String.format(CacheFun.KEY_FORMAT, keyIdxOffset + i), new Object());
                }
                else {
                    testMap.put(String.format(CacheFun.KEY_FORMAT, keyIdxOffset + i), new Object());
                }
            }
            keyIdxOffset += opsPerIterCount;
            iterations--;
        }
        return new Date().getTime() - start;
    }

}

class MoreGetsCacheLoadCheckCallable extends CacheLoadCheckCallable {


    public MoreGetsCacheLoadCheckCallable(Map<String, Object> testMap, int iterations) {
        super(testMap, iterations);
    }

    @Override
    public Long call() throws Exception {

        long start = new Date().getTime();
        while(iterations > 0) {
            Iterator<String> keyIter = testMap.keySet().iterator();
            String[] opKeys = new String[opsPerIterCount];
            for (int i = 0; i < opsPerIterCount; i++) {
                if (keyIter.hasNext()) {
                    opKeys[i] = keyIter.next();
                }
                else if (i > 0) {
                    opKeys[i] = opKeys[i - 1];
                }
                else {
                    opKeys[i] = "dummy";
                }
            }

            for (int i = 0; i < opsPerIterCount; i++) {
                if (i % 2 == 0) {
                    testMap.get(opKeys[i]);
                    testMap.get(opKeys[opsPerIterCount - i - 1]);
                    testMap.get(opKeys[opsPerIterCount - i - 1]);
                }
                else if (i % 3 == 0) {
                    testMap.get(opKeys[i]);
                    testMap.get(opKeys[opsPerIterCount - i - 1]);
                    testMap.get(opKeys[i]);
                    testMap.get(opKeys[opsPerIterCount - i - 1]);
                    testMap.put(String.format(CacheFun.KEY_FORMAT, keyIdxOffset + i), new Object());
                }
                else {
                    testMap.put(String.format(CacheFun.KEY_FORMAT, keyIdxOffset + i), new Object());
                }
            }
            keyIdxOffset += opsPerIterCount;
            iterations--;
        }
        return new Date().getTime() - start;
    }

}