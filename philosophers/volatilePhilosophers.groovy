class Chopstick {
    private static int count = 0;
    private int idx = 0 
    volatile Object occupied = null
    
    public Chopstick() {
        this.idx = count++;
    }
    
    public String toString() {
        "Chopstick #$idx"
    }
    
    public boolean lock(Object holder) {
        if (occupied == null) {
            occupied = holder
            //return occupied == holder
// data race check for simultaneous grabs
            int check = 2 //2 threads at the same time might be fighting for a stick
            while (occupied == holder && check > 0) {
                check--;
            }
            return check == 0 && occupied == holder

        }
        else {
            return false
        }
    }
    
    public void unlock(Object holder) {
        if (occupied == holder) {
            occupied = null
        }
    }
}

def names = ['Confucius'
    , 'Kant'
    , 'Nizche'
    , 'Socrates'
    , 'Diogenes'
]

def sticks = (0..4).collect {
    new Chopstick()
}

def threadsInCriticalSection = new java.util.concurrent.CopyOnWriteArrayList<String>()
def waitCountSamples = new java.util.concurrent.CopyOnWriteArrayList<Integer>()
def waitingThreadsSamples = new java.util.concurrent.CopyOnWriteArrayList<Integer>()
def waitingThreads = new java.util.concurrent.atomic.AtomicInteger(0)
def iterationsToThreadMap = [:]
def waitToThreadMap = [:]

def threads = (0..4).collect { i ->
    final Chopstick left = sticks[i]
    final Chopstick right = sticks[i == 4 ? 0 : i + 1]
    iterationsToThreadMap[names[i]] = new java.util.concurrent.atomic.AtomicInteger(0) 
    waitToThreadMap[names[i]] = new java.util.concurrent.atomic.AtomicInteger(0) 
    Thread.startDaemon(names[i]) {
        def random = new Random()
        def myName = Thread.currentThread().getName()
        println "$left <-- ${String.format('%-10s', myName)} --> $right"
        def iterations = iterationsToThreadMap[myName]
        def waitOverWork = waitToThreadMap[myName]
        int waitCount = 0
        try {
            while (!Thread.currentThread().isInterrupted()) {
                iterations.incrementAndGet()
                //println "$myName starving"
                waitingThreadsSamples.add(waitingThreads.incrementAndGet())
                waitCount++
                waitOverWork.incrementAndGet()                
                if (left.lock(myName)) {
                    int tries = Math.abs(random.nextInt(10))
                    while (!Thread.currentThread().isInterrupted() && tries-- > 0) {
                        if (right.lock(myName)) {
                            if (threadsInCriticalSection.size() > 1) { //depends on number of sticks
                                sticks.each {
                                    println "$it : $it.occupied"
                                }
                                println "_______________"
                                throw new IllegalStateException("$myName; $left: ${left.occupied}; $right: ${right.occupied}; too many eaters: $threadsInCriticalSection")
                            }
                            threadsInCriticalSection.add(myName)
                            Thread.sleep(Math.abs(random.nextInt(10))) //eating simulation to balance sequintial start
                            //println "$myName eating"
                            waitCountSamples.add(waitCount)
                            waitingThreadsSamples.add(waitingThreads.decrementAndGet())
                            waitOverWork.decrementAndGet()
                            waitCount = 0
                            threadsInCriticalSection.remove(myName)
                            right.unlock(myName)
                            left.unlock(myName)
                            break;
                        }
                    }
                }
                left.unlock(myName)
            }
        }
        catch (InterruptedException ie) {
            Thread.interrupted()
        }
        catch (Exception ex) {
            ex.printStackTrace()
            if (left.occupied == myName) {
                left.occupied = null
            }
            if (right.occupied == myName) {
                right.occupied = null
            }
        }
    }
}

Thread.sleep(30000)
threads.each {
    it.interrupt()
}

Thread.sleep(100)
sticks.each {
    println "$it : $it.occupied"
}
println ""
println ""
println String.format("%-11s|%-11s|%-11s|%-11s|%-12s", 'Philosopher', 'total', 'successful', 'unsuccesful', 'u/t ratio')
println "------------------------------------------------------------"
int starvedPhilosophers = 0
def maxWaits = waitCountSamples.max()
def iterationsSamples = iterationsToThreadMap.values().collect{ it.get() }
int maxSuccessful = 0
names.each {
    int total = iterationsToThreadMap[it].get()
    int lost = waitToThreadMap[it]
    if (maxSuccessful < total - lost) {
        maxSuccessful = total - lost
    }    
}
names.each {
    int total = iterationsToThreadMap[it].get()
    int lost = waitToThreadMap[it]
    int successful = total - lost
    if (total == lost || successful/maxSuccessful < 0.1) {
        starvedPhilosophers++
        println String.format("%-11s|%-11s|%-11s|%-11s|%-12s<----------starved", it, total, successful, lost, lost/total)
    }
    else {
        println String.format("%-11s|%-11s|%-11s|%-11s|%-12s", it, total, successful, lost, lost/total)
    }
}
println "----------"
println starvedPhilosophers > 0 ? "$starvedPhilosophers philosopher${ starvedPhilosophers > 1 ? 's' : ''} starved" : 'nobody starved'
println "----------"
if (!waitCountSamples.isEmpty()) {
    println "Waits per thread;"// $waitCountSamples"
    println "Max: $maxWaits"
    println "Avg: ${waitCountSamples.sum()/waitCountSamples.size()}"
    println "Last 15: ${waitCountSamples.size() > 15 ? waitCountSamples.subList(waitCountSamples.size() - 16, waitCountSamples.size() - 1) : waitCountSamples}"
    println "----------"
}
if (!waitingThreadsSamples.isEmpty()) {
    def maxWaiting = waitingThreadsSamples.max() 
    println "Starve/eat balance;"// $waitingThreadsSamples"
    println "Min: ${waitingThreadsSamples.min()}"
    println "Max: $maxWaiting"
    println "Waits to iterations ratio: ${iterationsSamples.size() > 0 ? maxWaiting/iterationsSamples.sum() : 'no data'}"
    println "Last 15: ${waitingThreadsSamples.size() > 15 ? waitingThreadsSamples.subList(waitingThreadsSamples.size() - 16, waitingThreadsSamples.size() - 1) : waitingThreadsSamples}"
    println "----------"                        
}
println "Experiment finished"